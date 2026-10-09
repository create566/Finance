package com.example.demo.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.demo.entity.Contract;
import com.example.demo.enums.ContractBizTypeEnum;
import com.example.demo.enums.ContractTypeEnum;

import java.util.Map;

/**
 * 电子合同服务
 * <p>
 * 设计约定：
 * <ol>
 *   <li>合同在业务对象**生成时**就预创建（如建标即生成《借款合同》），状态=待签署，
 *       而不是等到发布/投资/放款那一刻才生成——避免把生成逻辑塞进资金事务。</li>
 *   <li>资金动作前调用 {@link #assertSigned} 做强校验，未签直接拒绝。</li>
 *   <li>签署后正文快照冻结（content_snapshot + content_hash），模板改版不影响历史合同。</li>
 * </ol>
 */
public interface ContractService extends IService<Contract> {

    /**
     * 获取或创建合同：同一 (bizType, bizId, contractType, partyId) 只会有一份。
     * 合同正文在创建时用模板变量渲染并冻结，同时计算 SHA-256。
     *
     * @param bizType    业务对象类型
     * @param bizId      业务主键（如标的 id）
     * @param type       合同类型
     * @param partyId    甲方 userId（出借协议传投资人 id，借款/放款合同传借款人 id）
     * @param amount     合同金额；传 null 时取标的金额。出借协议必须传投资人本次拟投金额，
     *                   后续投资金额若与合同不符会要求重新签署（防止"签 1000 投 10 万"）
     * @return 已存在则返回原合同，否则新建
     */
    Contract getOrCreate(ContractBizTypeEnum bizType, Long bizId, ContractTypeEnum type, Long partyId,
                         java.math.BigDecimal amount);

    /**
     * 金额取标的金额的便捷重载
     */
    default Contract getOrCreate(ContractBizTypeEnum bizType, Long bizId, ContractTypeEnum type, Long partyId) {
        return getOrCreate(bizType, bizId, type, partyId, null);
    }

    /**
     * 【P9-60 / P9-61】用户级一次性协议：获取或生成《居间服务协议》/《资金存管协议》。
     *
     * <p>这类协议不针对某个标的，而是"用户 ↔ 平台"（居间）与"用户 ↔ 存管银行"（存管）的
     * 框架性约定，全平台一个用户只需签一次。落库形态：{@code biz_type=USER}、
     * {@code biz_id=userId}、{@code party_a_id=userId}、{@code party_b_id=NULL}、
     * {@code amount=0}（不能用 NULL，否则 MySQL 唯一键不去重会产生重复行）。</p>
     *
     * <p>模板未配置时返回 {@code null}（由调用方决定是否放行），不抛异常 ——
     * 避免"模板脚本还没执行"就把绑卡/发标/投标整条链路卡死。</p>
     *
     * @param type   仅接受 {@link ContractTypeEnum#SERVICE} 与 {@link ContractTypeEnum#DEPOSIT}
     * @param userId 用户 id
     * @return 合同（已存在则返回原合同）；模板缺失时返回 null
     */
    Contract getOrCreateUserAgreement(ContractTypeEnum type, Long userId);

    /**
     * 【P9-60 / P9-61】用户级协议签署前置强校验。
     *
     * <p>先按需生成（见 {@link #getOrCreateUserAgreement}），再校验是否已签署；
     * 未签署则抛出"请先签署《X》，可在「我的合同」中完成签署"。
     * <b>模板缺失时直接放行</b>（降级为不阻断），保证部署顺序不敏感。</p>
     */
    void assertUserAgreementSigned(ContractTypeEnum type, Long userId);

    /**
     * 【P9-65】导出合同正文（供前端下载）。
     *
     * @return {fileName, content}；调用方需校验可见性（本方法内部已校验）
     */
    Map<String, Object> exportFile(Long contractId, Long userId);

    /**
     * 签署前置强校验：未签署则抛出业务异常，由全局异常处理器转成标准返回。
     *
     * @param amount 本次业务金额（出借协议用于比对合同金额是否一致），无则传 null
     * @throws com.wwfinance.common.exception.ResourceNotFoundException 未签署/金额不符/合同不存在时
     */
    void assertSigned(ContractBizTypeEnum bizType, Long bizId, ContractTypeEnum type, Long partyId,
                      java.math.BigDecimal amount);

    /**
     * 手写签名落库（幂等：同一人重复提交不会重复写存证）。
     *
     * @param contractId     合同 id
     * @param signerId       签署人 userId（必须是合同甲方或乙方）
     * @param signImgBase64  手写签名图 base64（可带 data:image/png;base64, 前缀）
     * @param ip             签署 IP
     * @param userAgent      浏览器标识
     * @return 合同
     */
    Contract sign(Long contractId, Long signerId, String signImgBase64, String ip, String userAgent);

    /** 我的合同分页（按甲方或乙方匹配当前用户） */
    Page<Contract> pageMy(Long userId, String contractType, Integer status, Integer pageNum, Integer pageSize);

    /** 合同详情：正文 + 签署存证 + 哈希校验结果 */
    Map<String, Object> detail(Long contractId, Long userId);

    /** 作废合同（仅待签署状态可作废） */
    void revoke(Long contractId, String reason);

    /**
     * 补建/同步当前用户的待签合同。
     * <p>
     * 合同模块上线前已存在的标的与投资记录没有对应合同，会导致「我的合同」是空的，
     * 且发布/投资/放款被关口拦住却看不到该签哪一份。本方法按业务数据反查并补齐：
     * <ul>
     *   <li>我作为借款人的标的（待发布/募资中）→《借款合同》；募资中的再加《放款确认书》</li>
     *   <li>我作为投资人的投资记录 →《出借协议》（金额取该笔投资金额）</li>
     * </ul>
     * 幂等：已存在的合同会被 {@link #getOrCreate} 直接返回，不重复生成。
     *
     * @return 本次同步的统计信息（新增/已存在/跳过）
     */
    Map<String, Object> syncMyContracts(Long userId);
}
