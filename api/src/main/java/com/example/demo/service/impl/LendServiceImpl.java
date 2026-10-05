package com.example.demo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.demo.entity.BorrowInfo;
import com.example.demo.entity.Contract;
import com.example.demo.entity.Lend;
import com.example.demo.entity.LendItem;
import com.example.demo.enums.ContractBizTypeEnum;
import com.example.demo.enums.ContractStatusEnum;
import com.example.demo.enums.ContractTypeEnum;
import com.example.demo.enums.LendStatusEnum;
import com.example.demo.mapper.LendMapper;
import com.example.demo.service.ContractService;
import com.example.demo.service.LendItemService;
import com.example.demo.service.LendService;
import com.example.demo.service.RecommendService;
import com.example.demo.utils.LendNoUtils;
import com.wwfinance.common.exception.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import com.example.demo.utils.PageUtils;

@Slf4j
@Service
public class LendServiceImpl extends ServiceImpl<LendMapper, Lend> implements LendService {

    @Autowired
    private com.example.demo.service.MessageService messageService;

    @Autowired
    private LendItemService lendItemService;

    @Autowired
    private ContractService contractService;

    @Autowired
    private RecommendService recommendService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /** 与 LendItemServiceImpl 共用同一 key，撤标后必须清掉，否则剩余额度缓存是脏的 */
    private static final String LEFT_AMOUNT_KEY = "lend:leftAmount:";

    /** 标的金额下限（元） */
    private static final BigDecimal AMOUNT_MIN = new BigDecimal("100");
    /** 标的金额上限（元） */
    private static final BigDecimal AMOUNT_MAX = new BigDecimal("1000000");
    /** 年化利率下限（%） */
    private static final BigDecimal YEAR_RATE_MIN = new BigDecimal("5");
    /** 年化利率上限（%）：LPR的4倍，合规要求 */
    private static final BigDecimal YEAR_RATE_MAX = new BigDecimal("14.6");
    /** 借款期数下限 */
    private static final int PERIOD_MIN = 1;
    /** 借款期数上限 */
    private static final int PERIOD_MAX = 36;
    /** 标的募集天数（超时未募满则流标） */
    private static final int FUNDING_DAYS = 7;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Lend createLend(BorrowInfo borrowInfo) {
        Lend lend = new Lend();
        lend.setLendNo(LendNoUtils.getLendNo());
        lend.setTitle("借款标-" + borrowInfo.getUserId() + "-" + borrowInfo.getAmount());
        lend.setUserId(borrowInfo.getUserId());
        lend.setBorrowInfoId(borrowInfo.getId());
        lend.setAmount(borrowInfo.getAmount());
        lend.setInvestAmount(BigDecimal.ZERO);
        lend.setInvestNum(0);
        lend.setPeriod(borrowInfo.getPeriod());
        // 利率从借款申请拷贝，不重新计算（表字段为 lend_year_rate）
        lend.setLendYearRate(borrowInfo.getBorrowYearRate());
        lend.setReturnMethod(borrowInfo.getReturnMethod());
        lend.setLowestAmount(new BigDecimal("100"));
        lend.setStatus(LendStatusEnum.PENDING.getCode());
        lend.setPublishDate(LocalDateTime.now());
        lend.setCreateTime(LocalDateTime.now());
        lend.setUpdateTime(LocalDateTime.now());
        this.save(lend);
        // 建标即预生成《借款合同》（状态=待签署）：借款人有充足时间签，
        // 发布时只做校验，不把合同生成塞进发布事务
        contractService.getOrCreate(ContractBizTypeEnum.LEND, lend.getId(),
                ContractTypeEnum.LOAN, lend.getUserId());
        // 《放款确认书》也在建标时预生成（幂等）：借款人在募资期间即可提前签，
        // 不必等到满标。**发布时不强制**（详见 publish 处的说明），
        // 真正把关在放款前（LendReturnServiceImpl.buildMakeLoanForm 的关口三）。
        contractService.getOrCreate(ContractBizTypeEnum.LEND, lend.getId(),
                ContractTypeEnum.PAYMENT, lend.getUserId());
        return lend;
    }

    @Override
    public Page<Lend> pageList(Integer pageNum, Integer pageSize, String keyword, Integer status) {
        Page<Lend> pageParam = PageUtils.of(pageNum, pageSize);
        LambdaQueryWrapper<Lend> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(status != null, Lend::getStatus, status);
        if (keyword != null && !keyword.trim().isEmpty()) {
            wrapper.and(w -> w.like(Lend::getTitle, keyword)
                    .or().like(Lend::getLendNo, keyword));
        }
        wrapper.orderByDesc(Lend::getCreateTime);
        return this.page(pageParam, wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean offline(Long lendId, Long adminId) {
        // 处置投资人冻结资金是高风险动作，必须能追责到人。
        // 识别不到操作人时直接拒绝，而不是写成匿名记录留个空洞的留痕。
        if (adminId == null) {
            throw new ResourceNotFoundException(500, "无法识别操作人，已拒绝下架：处置投资人资金必须留痕");
        }
        Lend lend = this.getById(lendId);
        if (lend == null) {
            throw new ResourceNotFoundException(500, "标的不存在");
        }
        // 准入必须包含"满标待放款(COMPLETED)"：借款人不签《放款确认书》时标的会卡死在该状态，
        // 投资人资金既不能放款也不能退回，而 failLend 只扫募资中、cancelInvest 拒绝 status=2，
        // 下架是当时唯一的处置出口。缺了它，资金就是无限期悬停（资金池风险）。
        if (!LendStatusEnum.FUNDING.getCode().equals(lend.getStatus())
                && !LendStatusEnum.PENDING.getCode().equals(lend.getStatus())
                && !LendStatusEnum.COMPLETED.getCode().equals(lend.getStatus())) {
            throw new ResourceNotFoundException(500, "只有待发布/募资中/满标待放款标的才能下架");
        }

        // 1. 下架即退出募集，已投的钱必须原路退回。
        //    此前这里只翻状态位：投资人的钱一直冻在银行、合同停在"待签署"、双方都收不到通知。
        //    待发布(0)的标的本就没有投资记录，此处为空跑。
        List<LendItem> items = withdrawAllInvestments(lend);

        // 2. 作废该标下所有"待签署"合同（预生成的《借款合同》+ 已产生的《出借协议》）。
        //    已签署的合同按业务不可单方作废，单独兜住，绝不因一份脏数据阻断整笔下架。
        int revoked = revokePendingContracts(lend, "标的已下架：" + lend.getLendNo());

        // 3. 翻转标的状态
        lend.setStatus(LendStatusEnum.OFFLINE.getCode());
        //    下架留痕：记录谁退了投资人的资金、什么时候退的。与 check_*(上架审核)、
        //    payment_*(放款) 构成 "进 → 放 → 退" 三段完整链路。
        lend.setOfflineAdminId(adminId);
        lend.setOfflineTime(LocalDateTime.now());
        lend.setUpdateTime(LocalDateTime.now());
        boolean ok = this.updateById(lend);
        if (ok) {
            // 可推荐标的池发生变化 → 推荐缓存整体失效（version 自增，不做 KEYS 扫描）
            recommendService.evictAll();
        }

        // 4. 通知双方：借款人 + 每一位投资人。此前下架是"静默"的，用户只能自己发现标的不见了
        messageService.sendLendOfflineNotice(lend.getUserId(), lend.getLendNo(), lend.getAmount());
        for (LendItem item : items) {
            messageService.sendLendOfflineRefundNotice(
                    item.getInvestUserId(), item.getInvestAmount(), lend.getTitle());
        }

        log.info("标的 {} 下架完成：退还投资 {} 笔，作废待签合同 {} 份，操作人 adminId={}",
                lendId, items.size(), revoked, adminId);
        return ok;
    }

    /**
     * 把某标的下所有"已投标(0)"记录批量撤标，并退还投资人被冻结的资金。
     *
     * <p>平台不持资金：这里只把投资记录置为"已撤标(1)"，银行侧依据冻结关系自动解冻，
     * 与 {@code LendItemServiceImpl#cancelInvest} 是同一套机制。同时清零标的已投金额、
     * 删除 Redis 剩余额度缓存，避免标的下架/流标后缓存里仍留着可投余额。</p>
     *
     * <p>撤标范围必须覆盖 <b>投标中(0)</b> 与 <b>满标待放款(2)</b> 两种记录：
     * 满标时 {@code LendItemServiceImpl} 会把该标的全部投资统一置为 2，
     * 只捞 status=0 的话，"满标待放款"标的退资会静默空跑 —— 投资人资金冻在银行
     * 却一笔都没退，且没有任何报错。</p>
     *
     * @return 被撤标的投资记录（供调用方逐个发通知，因此不在此处发投资人消息）
     */
    private List<LendItem> withdrawAllInvestments(Lend lend) {
        List<LendItem> items = lendItemService.list(new LambdaQueryWrapper<LendItem>()
                .eq(LendItem::getLendId, lend.getId())
                .in(LendItem::getStatus, 0, 2));
        if (items.isEmpty()) {
            return items;
        }
        for (LendItem item : items) {
            item.setStatus(1);
            item.setUpdateTime(LocalDateTime.now());
            lendItemService.updateById(item);
        }
        lend.setInvestAmount(BigDecimal.ZERO);
        lend.setUpdateTime(LocalDateTime.now());
        this.updateById(lend);
        redisTemplate.delete(LEFT_AMOUNT_KEY + lend.getId());
        log.info("标的 {} 已退还 {} 笔投资，冻结资金由银行侧自动解冻", lend.getId(), items.size());
        return items;
    }

    /**
     * 作废某标的下所有"待签署"合同。
     *
     * <p>该标的的全部合同都落在 {@code biz_type='LEND' + biz_id=lendId} 下，
     * 再由 {@code contract_type}(LOAN/INVEST/PAYMENT) 与 {@code party_b_id} 区分当事人，
     * 因此一次查询即可覆盖《借款合同》与所有《出借协议》。</p>
     *
     * <p>已签署的合同不在待作废之列；单份失败只告警不抛出。</p>
     */
    private int revokePendingContracts(Lend lend, String reason) {
        List<Contract> contracts = contractService.list(new LambdaQueryWrapper<Contract>()
                .eq(Contract::getBizType, ContractBizTypeEnum.LEND.getCode())
                .eq(Contract::getBizId, lend.getId())
                .eq(Contract::getStatus, ContractStatusEnum.PENDING.getCode()));
        int revoked = 0;
        for (Contract c : contracts) {
            try {
                contractService.revoke(c.getId(), reason);
                revoked++;
            } catch (Exception e) {
                log.warn("下架时作废合同失败，已跳过：contractNo={}, err={}",
                        c.getContractNo(), e.getMessage());
            }
        }
        return revoked;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean publish(Long lendId, Long adminId) {
        Lend lend = this.getById(lendId);
        if (lend == null) {
            throw new ResourceNotFoundException(500, "标的不存在");
        }
        if (!LendStatusEnum.PENDING.getCode().equals(lend.getStatus())) {
            throw new ResourceNotFoundException(500, "只有待发布标的才能上架");
        }

        // 签署关口一：借款人未签《借款合同》不允许上架募资（合同在 createLend 时已预生成）
        contractService.assertSigned(ContractBizTypeEnum.LEND, lendId,
                ContractTypeEnum.LOAN, lend.getUserId(), null);
        // 《放款确认书》**不在此处强制校验**。
        //     它是"确认放款、授权划款"的意思表示，标的尚未募满就要求借款人确认放款，
        //     法理上说不通，也不符合行业惯例。正确时机是"满标后、放款前"，
        //     由 LendReturnServiceImpl.buildMakeLoanForm 的签署关口三把关。
        //     此处仅幂等预生成：借款人在募资期间可自愿提前签，避免满标后卡住。
        //     资金悬停风险改由三条措施兜住，而不是靠提前强签：
        //       ① 满标即发催签站内信（MessageTypeEnum.MAKE_LOAN_PENDING）
        //       ② offline() 放开满标待放款标的的退资出口（资金不会被永久冻结）
        //       ③ 合同模板约定超期未签的处置条款（给处置提供事先约定依据）
        contractService.getOrCreate(ContractBizTypeEnum.LEND, lendId,
                ContractTypeEnum.PAYMENT, lend.getUserId());

        // 风控参数校验：金额/利率/期限必须在产品规则范围内
        if (lend.getAmount() == null
                || lend.getAmount().compareTo(AMOUNT_MIN) < 0
                || lend.getAmount().compareTo(AMOUNT_MAX) > 0) {
            throw new ResourceNotFoundException(500,
                    "标的金额须在 " + AMOUNT_MIN + " ~ " + AMOUNT_MAX + " 元之间");
        }
        if (lend.getLendYearRate() == null
                || lend.getLendYearRate().compareTo(YEAR_RATE_MIN) < 0
                || lend.getLendYearRate().compareTo(YEAR_RATE_MAX) > 0) {
            throw new ResourceNotFoundException(500,
                    "年化利率须在 " + YEAR_RATE_MIN + "% ~ " + YEAR_RATE_MAX + "% 之间");
        }
        if (lend.getPeriod() == null
                || lend.getPeriod() < PERIOD_MIN
                || lend.getPeriod() > PERIOD_MAX) {
            throw new ResourceNotFoundException(500,
                    "借款期限须在 " + PERIOD_MIN + " ~ " + PERIOD_MAX + " 期之间");
        }

        lend.setStatus(LendStatusEnum.FUNDING.getCode());
        lend.setPublishDate(LocalDateTime.now());
        // 募集截止时间 = 发布时间 + FUNDING_DAYS 天，超时未募满则流标
        lend.setLendEndDate(LocalDateTime.now().plusDays(FUNDING_DAYS).toLocalDate());
        // 上架审核留痕：发布即审核通过，记录操作管理员与时间（此前这两个字段全项目从未写入）
        lend.setCheckTime(LocalDateTime.now());
        lend.setCheckAdminId(adminId);
        lend.setUpdateTime(LocalDateTime.now());
        boolean ok = this.updateById(lend);
        if (ok) {
            // 新标的上架 → 推荐池变化，整体失效推荐缓存
            recommendService.evictAll();
        }
        // 标的发布通知（给借款人）
        messageService.sendLendPublishNotice(lend.getUserId(), lend.getLendNo(), lend.getAmount());
        return ok;
    }

    @Override
    public Page<Lend> listFundingLends(Integer pageNum, Integer pageSize) {
        Page<Lend> pageParam = PageUtils.of(pageNum, pageSize);
        LambdaQueryWrapper<Lend> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Lend::getStatus, LendStatusEnum.FUNDING.getCode());
        wrapper.orderByDesc(Lend::getPublishDate);
        return this.page(pageParam, wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean failLend(Long lendId) {
        Lend lend = this.getById(lendId);
        if (lend == null) {
            throw new ResourceNotFoundException(500, "标的不存在");
        }
        // 只有募资中的标的才能流标（保证幂等，避免重复处理）
        if (!LendStatusEnum.FUNDING.getCode().equals(lend.getStatus())) {
            log.warn("标的 {} 状态不是募资中，跳过流标，当前状态={}", lendId, lend.getStatus());
            return false;
        }

        // 1~2. 批量标记为已撤标(1)，银行自动解冻投资人冻结资金（与"下架"共用同一套逻辑）
        List<LendItem> items = withdrawAllInvestments(lend);

        // 3. 标的已投金额清零，状态改为已流标
        lend.setInvestAmount(BigDecimal.ZERO);
        lend.setStatus(LendStatusEnum.FAILED.getCode());
        lend.setUpdateTime(LocalDateTime.now());
        this.updateById(lend);

        // 标的退出募资池 → 推荐缓存整体失效
        recommendService.evictAll();

        // 4. 发送流标通知：给借款人 + 每个投资人
        messageService.sendLendFailNotice(lend.getUserId(), lend.getLendNo(), lend.getAmount());
        for (LendItem item : items) {
            messageService.send(item.getInvestUserId(),
                    com.example.demo.enums.MessageTypeEnum.LEND_FAIL,
                    "【标的流标】",
                    "您投资的标的 " + lend.getLendNo() + " 因募集超时已流标，冻结资金 "
                            + item.getInvestAmount() + " 元已解冻退回");
        }

        log.info("标的 {} 流标处理完成，共解冻 {} 笔投资，金额合计 {} 元",
                lendId, items.size(), lend.getAmount());
        return true;
    }
}