package com.example.demo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.demo.entity.TransFlow;
import com.example.demo.entity.User;
import com.example.demo.entity.UserBind;
import com.example.demo.enums.TransTypeEnum;
import com.example.demo.exception.BusinessException;
import com.example.demo.exception.ResourceNotFoundException;
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
import java.util.Map;

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
        // 平台不操作余额，仅记录交易流水
        saveTransFlow(user, TransTypeEnum.RECHARGE.getCode(), TransTypeEnum.RECHARGE.getDesc(),
                amount, "用户充值", bankTransNo);
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
        // 平台不操作余额，仅记录交易流水
        saveTransFlow(user, TransTypeEnum.WITHDRAW.getCode(), TransTypeEnum.WITHDRAW.getDesc(),
                amount, "用户提现", bankTransNo);
        userAccountService.updateBalance(userId, TransTypeEnum.WITHDRAW, amount);
        log.info("提现流水已记录，userId={}, amount={}", userId, amount);
        messageService.sendWithdrawNotice(userId, amount);
    }

    @Override
    public boolean existsByTransNo(String transNo) {
        LambdaQueryWrapper<TransFlow> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TransFlow::getTransNo, transNo);
        return this.count(wrapper) > 0;
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
        String transNo = (String) params.getOrDefault("transNo", params.get("agentBillNo"));
        String status = (String) params.getOrDefault("status", params.get("resultCode"));

        if ("0001".equals(status)) {
            status = BANK_STATUS_SUCCESS;
        }

        if (!BANK_STATUS_SUCCESS.equals(status)) {
            log.warn("充值回调银行状态非成功，status={}", status);
            return true;
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
        // 幂等：同一银行流水号不重复记录
        if (transNo != null && existsByTransNo(transNo)) {
            log.info("充值回调重复通知，直接确认，transNo={}", transNo);
            return true;
        }
        // 平台不操作余额，仅记录流水
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
        String transNo = (String) params.getOrDefault("transNo", params.get("agentBillNo"));
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
        // 幂等：成功流水和冲正流水共用同一银行流水号维度（冲正落库带 _REVERSAL 后缀，需一并查）
        if (transNo != null && (existsByTransNo(transNo) || existsByTransNo(transNo + "_REVERSAL"))) {
            log.info("提现回调重复通知，直接确认，transNo={}", transNo);
            return true;
        }
        // 银行失败 → 记冲正流水
        if (!BANK_STATUS_SUCCESS.equals(status)) {
            saveTransFlow(user, TransTypeEnum.WITHDRAW.getCode(), TransTypeEnum.WITHDRAW.getDesc(),
                    new BigDecimal(amountStr), "提现失败冲正 " + transNo, transNo + "_REVERSAL");
            log.warn("提现回调银行失败，冲正流水已记录，userId={}", user.getId());
            return true;
        }
        // 银行成功 → 记正常流水
        withdraw(user.getId(), new BigDecimal(amountStr), transNo);
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
     * 记录交易流水
     */
    private void saveTransFlow(User user, int transType, String transTypeName,
                                   BigDecimal amount, String memo, String bankTransNo) {
        String transNo = (bankTransNo != null && !bankTransNo.isEmpty())
                ? bankTransNo : LendNoUtils.getTransNo();
        TransFlow transFlow = new TransFlow()
                .setUserId(user.getId())
                .setUserName(user.getName())
                .setTransNo(transNo)
                .setTransType(transType)
                .setTransTypeName(transTypeName)
                .setTransAmount(amount)
                .setMemo(memo);
        this.save(transFlow);
    }

    @Override
    public IPage<TransFlow> listByUser(Long userId, long page, long limit) {
        Page<TransFlow> pageParam = new Page<>(page, limit);
        LambdaQueryWrapper<TransFlow> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TransFlow::getUserId, userId)
                .orderByDesc(TransFlow::getCreateTime);
        return baseMapper.selectPage(pageParam, wrapper);
    }

    @Override
    public IPage<TransFlow> adminPageList(Integer pageNum, Integer pageSize,
                                          Long userId, Integer transType, String userName) {
        Page<TransFlow> pageParam = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<TransFlow> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(userId != null, TransFlow::getUserId, userId);
        wrapper.eq(transType != null, TransFlow::getTransType, transType);
        wrapper.like(userName != null && !userName.trim().isEmpty(),
                TransFlow::getUserName, userName);
        wrapper.orderByDesc(TransFlow::getCreateTime);
        return baseMapper.selectPage(pageParam, wrapper);
    }
}