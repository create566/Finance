package com.example.demo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.demo.entity.TransFlow;
import com.example.demo.entity.User;
import com.example.demo.entity.UserBind;
import com.example.demo.enums.TransStatusEnum;
import com.example.demo.enums.TransTypeEnum;
import com.example.demo.exception.BusinessException;
import com.wwfinance.common.exception.ResourceNotFoundException;
import com.example.demo.mapper.TransFlowMapper;
import com.example.demo.mapper.UserMapper;
import com.example.demo.service.*;
import com.example.demo.utils.LendNoUtils;
import com.wwfinance.common.utils.BankSignUtil;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import com.example.demo.utils.PageUtils;

/**
 * 交易流水 Service 实现类
 *
 * 核心原则：平台（撮合方）不碰资金，只记录交易流水。
 * 所有余额变动由银行（ww_bank）处理，平台仅做以下工作：
 *   1. 组装银行表单 → 用户在银行完成资金操作
 *   2. 接收银行回调 → 记录交易流水（对账用）
 *   3. 查询银行余额 → 从银行获取真实余额数据
 *
 * 平台 user_account 表仅作为账本镜像，由 BankAccountSyncService 从银行同步。
 */
@Log4j2
@Service
public class TransFlowServiceImpl extends ServiceImpl<TransFlowMapper, TransFlow>
        implements TransFlowService {

    private static final String BANK_STATUS_SUCCESS = "1";
    private static final String AMOUNT_REGEX = "\\d+(\\.\\d{1,2})?";

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private UserAccountService userAccountService;

    @Autowired
    private UserService userService;

    @Autowired
    private UserBindService userBindService;

    @Autowired
    private BankAccountSyncService bankAccountSyncService;

    @Autowired
    private com.example.demo.service.UserIntegralService userIntegralService;

    @Autowired
    private com.example.demo.service.MessageService messageService;

    /**
     * 充值（仅记录流水，不操作余额）
     * 实际资金变动由银行处理，回调时记录流水
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recharge(Long userId, BigDecimal amount, String bankTransNo) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(400, "充值金额必须大于0");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new ResourceNotFoundException(404, "用户不存在，充值失败");
        }
        // 发起充值时已落过一条「处理中」流水，这里把它置为成功（找不到才补插）。
        // 平台不操作余额，仅记录交易流水
        settleFlow(user, TransTypeEnum.RECHARGE, amount, "用户充值",
                bankTransNo, TransStatusEnum.SUCCESS);
        userAccountService.updateBalance(userId, TransTypeEnum.RECHARGE, amount);
        int points = amount.intValue();
        if (points > 0) {
            userIntegralService.addIntegral(userId, points, "充值累计积分");
        }
        log.info("充值流水已记录，userId={}, amount={}", userId, amount);
        messageService.sendRechargeNotice(userId, amount);
    }

    /**
     * 提现（仅记录流水，不操作余额）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void withdraw(Long userId, BigDecimal amount, String bankTransNo) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(400, "提现金额必须大于0");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new ResourceNotFoundException(404, "用户不存在，提现失败");
        }
        // 发起提现时已落过一条「处理中」流水，这里把它置为成功
        settleFlow(user, TransTypeEnum.WITHDRAW, amount, "用户提现",
                bankTransNo, TransStatusEnum.SUCCESS);
        userAccountService.updateBalance(userId, TransTypeEnum.WITHDRAW, amount);
        log.info("提现流水已记录，userId={}, amount={}", userId, amount);
        messageService.sendWithdrawNotice(userId, amount);
    }

    @Override
    public boolean existsByTransNo(String transNo) {
        if (transNo == null || transNo.isEmpty()) {
            return false;
        }
        return this.count(new LambdaQueryWrapper<TransFlow>()
                .eq(TransFlow::getTransNo, transNo)) > 0;
    }

    @Override
    public boolean existsSettledByTransNo(String transNo) {
        if (transNo == null || transNo.isEmpty()) {
            return false;
        }
        return this.count(new LambdaQueryWrapper<TransFlow>()
                .eq(TransFlow::getTransNo, transNo)
                .ne(TransFlow::getStatus, TransStatusEnum.PROCESSING.getCode())) > 0;
    }

    /**
     * 取本次回调对应的业务单号。
     *
     * 充值/提现必须优先用平台自生成的 agentBillNo —— 用户在页面上点"充值"时，
     * 平台已经按这个号落了一条「处理中」流水，银行会把 agentBillNo 原样回带，
     * 用它才能找到并更新那条记录；换成银行的流水号就会对不上，
     * 结果是处理中流水永远挂着、又凭空多出一条成功流水。
     * 投标/放款/还款没有预留流水，用银行流水号即可。
     */
    private String resolveTransNo(Map<String, Object> params) {
        String agentBillNo = (String) params.get("agentBillNo");
        if (agentBillNo != null && !agentBillNo.trim().isEmpty()) {
            return agentBillNo.trim();
        }
        String bankTransNo = (String) params.getOrDefault("transNo", params.get("bankTransNo"));
        if (bankTransNo != null && !bankTransNo.trim().isEmpty()) {
            return bankTransNo.trim();
        }
        return LendNoUtils.getTransNo();
    }

    /**
     * 充值回调处理
     * 流程：验签 → 银行状态判断 → 金额校验 → 用户查询 → 幂等 → 记流水
     * 注意：平台不操作余额，银行处理资金，平台仅记录交易
     */
    @Override
    public boolean handleRechargeNotify(Map<String, Object> params) {
        log.info("充值回调收到参数：{}", params);
        if (!BankSignUtil.isSignEquals(params)) {
            log.warn("充值回调验签失败，params={}", params);
            return false;
        }
        String agentUserId = (String) params.get("agentUserId");
        String amountStr = (String) params.getOrDefault("amount", params.get("chargeAmt"));
        String transNo = resolveTransNo(params);
        String status = (String) params.getOrDefault("status", params.get("resultCode"));

        if ("0001".equals(status)) {
            status = BANK_STATUS_SUCCESS;
        }
        if (amountStr == null || !amountStr.matches(AMOUNT_REGEX)) {
            log.warn("充值回调金额非法，amount={}", amountStr);
            return false;
        }
        User user = resolveNotifyUser(params);
        if (user == null) {
            log.warn("充值回调用户不存在，agentUserId={}, bindCode={}",
                    agentUserId, params.get("bindCode"));
            return false;
        }
        // 幂等只看「终态」：发起充值时就落的那条处理中流水不算已入账，
        // 否则回调会被当成重复通知吞掉，那笔钱永远停在处理中
        if (existsSettledByTransNo(transNo)) {
            log.info("充值回调重复通知，直接确认，transNo={}", transNo);
            return true;
        }
        if (!BANK_STATUS_SUCCESS.equals(status)) {
            // 银行侧失败：把处理中流水置为失败并告知用户，
            // 不能让这笔单一直挂着"处理中"——用户会以为钱卡住了
            BigDecimal amount = new BigDecimal(amountStr);
            settleFlow(user, TransTypeEnum.RECHARGE, amount,
                    "充值失败（银行侧未成功）", transNo, TransStatusEnum.FAILED);
            messageService.sendRechargeFailNotice(user.getId(), amount);
            log.warn("充值回调银行状态非成功，已置失败，userId={}, transNo={}", user.getId(), transNo);
            return true;
        }
        recharge(user.getId(), new BigDecimal(amountStr), transNo);
        return true;
    }

    /**
     * 提现回调处理
     * 流程：验签 → 银行状态判断 → 金额校验 → 用户查询 → 幂等 → 记流水/冲正
     * 注意：平台不操作余额，银行处理资金，平台仅记录交易
     */
    @Override
    public boolean handleWithdrawNotify(Map<String, Object> params) {
        log.info("提现回调收到参数：{}", params);
        if (!BankSignUtil.isSignEquals(params)) {
            log.warn("提现回调验签失败，params={}", params);
            return false;
        }
        String agentUserId = (String) params.get("agentUserId");
        String amountStr = (String) params.getOrDefault("amount", params.get("fetchAmt"));
        String transNo = resolveTransNo(params);
        String status = (String) params.getOrDefault("status", params.get("resultCode"));

        if ("0001".equals(status)) {
            status = BANK_STATUS_SUCCESS;
        }
        if (amountStr == null || !amountStr.matches(AMOUNT_REGEX)) {
            log.warn("提现回调金额非法，amount={}", amountStr);
            return false;
        }
        User user = resolveNotifyUser(params);
        if (user == null) {
            log.warn("提现回调用户不存在，agentUserId={}, bindCode={}",
                    agentUserId, params.get("bindCode"));
            return false;
        }
        // 幂等：同上，处理中不算已入账
        if (existsSettledByTransNo(transNo)) {
            log.info("提现回调重复通知，直接确认，transNo={}", transNo);
            return true;
        }
        BigDecimal amount = new BigDecimal(amountStr);
        // 银行失败 → 把发起时落的那条处理中流水置为失败，并通知用户。
        // 注意：提现失败不需要"回滚余额" —— 平台在发起时就没扣钱，钱一直在用户账上。
        if (!BANK_STATUS_SUCCESS.equals(status)) {
            settleFlow(user, TransTypeEnum.WITHDRAW, amount,
                    "提现失败（银行侧未成功）", transNo, TransStatusEnum.FAILED);
            messageService.sendWithdrawFailNotice(user.getId(), amount);
            log.warn("提现回调银行失败，已置失败，userId={}, transNo={}", user.getId(), transNo);
            return true;
        }
        // 银行成功 → 置为成功并扣减平台账本镜像
        withdraw(user.getId(), amount, transNo);
        return true;
    }

    /**
     * 从银行回调参数中解析用户。
     * 绑卡回调会带 agentUserId（手机号）；充值/提现/投标/放款/还款回调只回传 bindCode。
     * 优先按 agentUserId 查，缺失时按 bindCode 反查绑卡记录再取用户。
     */
    private User resolveNotifyUser(Map<String, Object> params) {
        String agentUserId = (String) params.get("agentUserId");
        if (agentUserId != null && !agentUserId.isEmpty()) {
            return userService.getByMobile(agentUserId);
        }
        String bindCode = (String) params.get("bindCode");
        if (bindCode == null || bindCode.isEmpty()) {
            return null;
        }
        UserBind userBind = userBindService.getByBindCode(bindCode);
        if (userBind == null || userBind.getUserId() == null) {
            return null;
        }
        return userService.getById(userBind.getUserId());
    }

    /**
     * 把一笔交易落成终态流水。
     *
     * 顺序是「先找发起时落的那条处理中流水，找到就更新，找不到才插入」：
     * 充值/提现走"发起即落流水"，正常路径必定能找到；
     * 找不到说明是历史数据或有人绕过平台直接调银行，此时补插一条保持账目完整。
     */
    private void settleFlow(User user, TransTypeEnum type, BigDecimal amount, String memo,
                            String bankTransNo, TransStatusEnum result) {
        String transNo = (bankTransNo != null && !bankTransNo.isEmpty())
                ? bankTransNo : LendNoUtils.getTransNo();
        LocalDateTime now = LocalDateTime.now();

        TransFlow existed = this.getOne(new LambdaQueryWrapper<TransFlow>()
                .eq(TransFlow::getTransNo, transNo)
                .last("LIMIT 1"), false);
        if (existed != null) {
            existed.setStatus(result.getCode());
            existed.setTransAmount(amount);
            existed.setTransTypeName(type.getDesc());
            existed.setMemo(memo);
            existed.setUpdateTime(now);
            this.updateById(existed);
            return;
        }

        this.save(new TransFlow()
                .setUserId(user.getId())
                .setUserName(user.getName())
                .setTransNo(transNo)
                .setTransType(type.getCode())
                .setTransTypeName(type.getDesc())
                .setTransAmount(amount)
                .setStatus(result.getCode())
                .setMemo(memo)
                .setCreateTime(now)
                .setUpdateTime(now));
    }

    @Override
    public IPage<TransFlow> listByUser(Long userId, long page, long limit, Integer transType) {
        Page<TransFlow> pageParam = PageUtils.of(page, limit);
        LambdaQueryWrapper<TransFlow> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TransFlow::getUserId, userId)
                .eq(transType != null, TransFlow::getTransType, transType)
                .orderByDesc(TransFlow::getCreateTime);
        return baseMapper.selectPage(pageParam, wrapper);
    }

    @Override
    public IPage<TransFlow> adminPageList(Integer pageNum, Integer pageSize,
                                          Long userId, Integer transType, String userName) {
        Page<TransFlow> pageParam = PageUtils.of(pageNum, pageSize);
        LambdaQueryWrapper<TransFlow> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(userId != null, TransFlow::getUserId, userId);
        wrapper.eq(transType != null, TransFlow::getTransType, transType);
        wrapper.like(userName != null && !userName.trim().isEmpty(),
                TransFlow::getUserName, userName);
        wrapper.orderByDesc(TransFlow::getCreateTime);
        return baseMapper.selectPage(pageParam, wrapper);
    }
}