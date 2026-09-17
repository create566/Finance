package com.example.demo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.demo.entity.BorrowInfo;
import com.example.demo.entity.Lend;
import com.example.demo.entity.LendItem;
import com.example.demo.enums.LendStatusEnum;
import com.example.demo.mapper.LendMapper;
import com.example.demo.service.LendItemService;
import com.example.demo.service.LendService;
import com.example.demo.utils.LendNoUtils;
import com.wwfinance.common.exception.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class LendServiceImpl extends ServiceImpl<LendMapper, Lend> implements LendService {

    @Autowired
    private com.example.demo.service.MessageService messageService;

    @Autowired
    private LendItemService lendItemService;

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
        return lend;
    }

    @Override
    public Page<Lend> pageList(Integer pageNum, Integer pageSize, String keyword, Integer status) {
        Page<Lend> pageParam = new Page<>(pageNum, pageSize);
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
    public boolean offline(Long lendId) {
        Lend lend = this.getById(lendId);
        if (lend == null) {
            throw new ResourceNotFoundException(500, "标的不存在");
        }
        if (!LendStatusEnum.FUNDING.getCode().equals(lend.getStatus())
                && !LendStatusEnum.PENDING.getCode().equals(lend.getStatus())) {
            throw new ResourceNotFoundException(500, "只有待发布/募资中标的才能下架");
        }
        lend.setStatus(LendStatusEnum.OFFLINE.getCode());
        lend.setUpdateTime(LocalDateTime.now());
        return this.updateById(lend);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean publish(Long lendId) {
        Lend lend = this.getById(lendId);
        if (lend == null) {
            throw new ResourceNotFoundException(500, "标的不存在");
        }
        if (!LendStatusEnum.PENDING.getCode().equals(lend.getStatus())) {
            throw new ResourceNotFoundException(500, "只有待发布标的才能上架");
        }

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
        lend.setLendEndDate(LocalDateTime.now().plusDays(FUNDING_DAYS));
        lend.setUpdateTime(LocalDateTime.now());
        boolean ok = this.updateById(lend);
        // 标的发布通知（给借款人）
        messageService.sendLendPublishNotice(lend.getUserId(), lend.getLendNo(), lend.getAmount());
        return ok;
    }

    @Override
    public Page<Lend> listFundingLends(Integer pageNum, Integer pageSize) {
        Page<Lend> pageParam = new Page<>(pageNum, pageSize);
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

        // 1. 查询该标的所有"已投标(0)"的投资记录
        List<LendItem> items = lendItemService.list(new LambdaQueryWrapper<LendItem>()
                .eq(LendItem::getLendId, lendId)
                .eq(LendItem::getStatus, 0));

        // 2. 批量标记为已撤标(1)，银行自动解冻投资人冻结资金（与 cancelInvest 同一机制）
        for (LendItem item : items) {
            item.setStatus(1);
            item.setUpdateTime(LocalDateTime.now());
            lendItemService.updateById(item);
        }

        // 3. 标的已投金额清零，状态改为已流标
        lend.setInvestAmount(BigDecimal.ZERO);
        lend.setStatus(LendStatusEnum.FAILED.getCode());
        lend.setUpdateTime(LocalDateTime.now());
        this.updateById(lend);

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
