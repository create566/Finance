package com.example.demo.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.demo.entity.LendItemReturn;
import com.example.demo.entity.LendReturn;

import java.util.List;

import java.util.Map;

/**
 * 还款 Service
 * 负责：满标放款、生成还款计划、借款人还款、投资人回款
 */
public interface LendReturnService extends IService<LendReturn> {

    /**
     * 发起满标放款（跳转旺旺银行）：
     * 校验标的满标待放款 → 组装银行放款参数+签名 → 返回自动提交表单HTML
     * 借款人入账不在本方法执行，由银行异步回调驱动（合规：资金不过平台账户）
     *
     * @param lendId 标的ID
     * @return 跳转到银行页面的 HTML 表单串
     */
    String buildMakeLoanForm(Long lendId, Long adminId);

    /**
     * 处理银行放款异步回调：验签 → 状态/金额校验 → 幂等 → 生成还款计划 → 借款人入账 → 标的状态改还款中
     *
     * @param params 银行回调的全部参数（含 sign）
     * @return true=业务已受理（应答 success）；false=验签失败或数据异常（应答 fail）
     */
    boolean handleMakeLoanNotify(Map<String, Object> params);

    /**
     * 发起还款（跳转旺旺银行）：
     * 校验还款计划/借款人/绑卡 → 组装银行还款参数+签名 → 返回自动提交表单HTML
     * 借款人扣款不在本方法执行，由银行异步回调驱动（合规：资金不过平台账户）
     *
     * @param lendReturnId 还款计划ID
     * @param userId       借款人ID
     * @return 跳转到银行页面的 HTML 表单串
     */
    String buildRepayForm(Long lendReturnId, Long userId);

    /**
     * 处理银行还款异步回调：验签 → 状态/金额校验 → 幂等 → 扣借款人余额 → 投资人回款 → 更新计划状态 → 判断结清
     *
     * @param params 银行回调的全部参数（含 sign）
     * @return true=业务已受理（应答 success）；false=验签失败或数据异常（应答 fail）
     */
    boolean handleRepayNotify(Map<String, Object> params);

    /**
     * 借款人偿还某一期：
     * 扣借款人余额 → 按收益计划给各投资人入账 → 更新计划状态 → 判断是否结清
     *
     * @deprecated 此方法直接扣账户余额，绕过银行存管，仅用于单元测试。
     *             生产环境请走 {@link #handleRepayNotify(Map)} 银行回调入口。
     */
    @Deprecated
    void repay(Long lendReturnId, Long userId);

    /**
     * 借款人查询自己的还款计划
     */
    List<LendReturn> listByBorrower(Long userId);

    /**
     * 管理端：查询某标的的还款计划
     */
    List<LendReturn> listByLendId(Long lendId);

    /**
     * 查询某一期某投资人的收益明细（带归属校验）。
     *
     * <p>借款人可查看该期全部投资人明细（还款时需知道钱还给谁）；
     * 投资人只能查看自己那一条；与本期无关的用户查到空列表。</p>
     */
    List<LendItemReturn> listItemReturns(Long lendReturnId, Long userId);

    /**
     * 投资人查询自己的回款计划
     */
    List<LendItemReturn> listMyItemReturns(Long investUserId);

    /**
     * 逾期扫描：查找已过还款日但未还的还款计划
     * 标记逾期、按日计收罚息、标的置为逾期状态、给借款人发催收消息
     * 幂等：已逾期的只刷新罚息，不重复发送催收通知
     *
     * @return 本次处理的逾期记录数（含新标记 + 罚息刷新）
     */
    int overdueCheck();

}
