package com.example.demo.service.impl;

import com.example.demo.entity.TransFlow;
import com.example.demo.entity.User;
import com.example.demo.mapper.TransFlowMapper;
import com.example.demo.mapper.UserMapper;
import com.example.demo.service.MessageService;
import com.example.demo.service.UserAccountService;
import com.example.demo.service.UserIntegralService;
import com.example.demo.service.UserService;
import com.wwfinance.common.utils.BankSignUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TransFlowServiceImpl 银行回调处理单元测试
 * 纯 Mockito：验签用真实 BankSignUtil，Mapper/Service 全部 mock
 *
 * 注意：平台不碰资金，回调只记录交易流水，不操作余额。
 * 验证重点：验签、状态判断、金额校验、幂等、流水落库。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("交易流水回调业务（Service 层）测试")
class TransFlowNotifyServiceTest {

    @Mock
    private UserMapper userMapper;
    @Mock
    private UserAccountService userAccountService;
    @Mock
    private UserService userService;
    @Mock
    private UserIntegralService userIntegralService;
    @Mock
    private MessageService messageService;
    @Mock
    private TransFlowMapper baseMapper;

    @InjectMocks
    private TransFlowServiceImpl transFlowService;

    private User mockUser;

    @BeforeEach
    void setUp() {
        mockUser = new User();
        mockUser.setId(1L);
        mockUser.setMobile("13800138000");
        mockUser.setName("张三");
    }

    /** 先填业务字段，最后生成合法签名 */
    private Map<String, Object> signedParams(Map<String, Object> params) {
        params.put("sign", BankSignUtil.getSign(new HashMap<>(params)));
        return params;
    }

    private Map<String, Object> baseParams(String amount, String status, String transNo) {
        Map<String, Object> params = new HashMap<>();
        params.put("agentUserId", "13800138000");
        params.put("amount", amount);
        params.put("status", status);
        if (transNo != null) {
            params.put("transNo", transNo);
        }
        return signedParams(params);
    }

    // ==================== 充值回调 ====================

    @Test
    @DisplayName("充值：验签失败 → false，不记流水")
    void recharge_signInvalid_returnFalse() {
        Map<String, Object> params = new HashMap<>();
        params.put("agentUserId", "13800138000");
        params.put("amount", "100.00");
        params.put("sign", "invalid-sign");

        assertFalse(transFlowService.handleRechargeNotify(params));

        verify(baseMapper, never()).insert(any(TransFlow.class));
    }

    @Test
    @DisplayName("充值：status!=1（银行处理失败）→ true 但不记流水（无需重试）")
    void recharge_statusNotOne_ackWithoutEntry() {
        Map<String, Object> params = baseParams("100.00", "0", "NO1");

        assertTrue(transFlowService.handleRechargeNotify(params));

        verify(userService, never()).getByMobile(any());
        verify(baseMapper, never()).insert(any(TransFlow.class));
    }

    @Test
    @DisplayName("充值：金额非法 → false，不记流水")
    void recharge_amountInvalid_returnFalse() {
        Map<String, Object> params = baseParams("abc", "1", "NO1");

        assertFalse(transFlowService.handleRechargeNotify(params));

        verify(baseMapper, never()).insert(any(TransFlow.class));
    }

    @Test
    @DisplayName("充值：用户不存在 → false")
    void recharge_userNotFound_returnFalse() {
        Map<String, Object> params = baseParams("100.00", "1", "NO1");
        when(userService.getByMobile("13800138000")).thenReturn(null);

        assertFalse(transFlowService.handleRechargeNotify(params));

        verify(baseMapper, never()).insert(any(TransFlow.class));
    }

    @Test
    @DisplayName("充值：流水号已入账（重复回调）→ true 且不重复记流水")
    void recharge_duplicateTransNo_idempotent() {
        Map<String, Object> params = baseParams("100.00", "1", "NO1");
        when(userService.getByMobile("13800138000")).thenReturn(mockUser);
        when(baseMapper.selectCount(any())).thenReturn(1);

        assertTrue(transFlowService.handleRechargeNotify(params));

        verify(baseMapper, never()).insert(any(TransFlow.class));
    }

    @Test
    @DisplayName("充值：正常回调 → true，记录流水")
    void recharge_normal_success() {
        Map<String, Object> params = baseParams("100.00", "1", "NO1");
        when(userService.getByMobile("13800138000")).thenReturn(mockUser);
        when(baseMapper.selectCount(any())).thenReturn(0);
        when(userMapper.selectById(1L)).thenReturn(mockUser);
        when(baseMapper.insert(any(TransFlow.class))).thenReturn(1);

        assertTrue(transFlowService.handleRechargeNotify(params));

        verify(baseMapper).insert(any(TransFlow.class));
        verify(messageService).sendRechargeNotice(eq(1L), eq(new BigDecimal("100.00")));
    }

    // ==================== 提现回调 ====================

    @Test
    @DisplayName("提现：验签失败 → false，不记流水")
    void withdraw_signInvalid_returnFalse() {
        Map<String, Object> params = new HashMap<>();
        params.put("agentUserId", "13800138000");
        params.put("amount", "50.00");
        params.put("sign", "invalid-sign");

        assertFalse(transFlowService.handleWithdrawNotify(params));

        verify(baseMapper, never()).insert(any(TransFlow.class));
    }

    @Test
    @DisplayName("提现：金额非法（三位小数）→ false")
    void withdraw_amountInvalid_returnFalse() {
        Map<String, Object> params = baseParams("1.999", "1", "NO2");

        assertFalse(transFlowService.handleWithdrawNotify(params));

        verify(baseMapper, never()).insert(any(TransFlow.class));
    }

    @Test
    @DisplayName("提现：用户不存在 → false")
    void withdraw_userNotFound_returnFalse() {
        Map<String, Object> params = baseParams("50.00", "1", "NO2");
        when(userService.getByMobile("13800138000")).thenReturn(null);

        assertFalse(transFlowService.handleWithdrawNotify(params));

        verify(baseMapper, never()).insert(any(TransFlow.class));
    }

    @Test
    @DisplayName("提现：流水号已入账（重复回调）→ true 且不重复记流水")
    void withdraw_duplicateTransNo_idempotent() {
        Map<String, Object> params = baseParams("50.00", "1", "NO2");
        when(userService.getByMobile("13800138000")).thenReturn(mockUser);
        when(baseMapper.selectCount(any())).thenReturn(1);

        assertTrue(transFlowService.handleWithdrawNotify(params));

        verify(baseMapper, never()).insert(any(TransFlow.class));
    }

    @Test
    @DisplayName("提现：正常回调 → true，记录流水")
    void withdraw_normal_success() {
        Map<String, Object> params = baseParams("50.00", "1", "NO2");
        when(userService.getByMobile("13800138000")).thenReturn(mockUser);
        when(baseMapper.selectCount(any())).thenReturn(0);
        when(userMapper.selectById(1L)).thenReturn(mockUser);
        when(baseMapper.insert(any(TransFlow.class))).thenReturn(1);

        assertTrue(transFlowService.handleWithdrawNotify(params));

        verify(baseMapper).insert(any(TransFlow.class));
        verify(messageService).sendWithdrawNotice(eq(1L), eq(new BigDecimal("50.00")));
    }

    @Test
    @DisplayName("提现：status!=1 → true 但记冲正流水")
    void withdraw_statusNotOne_ackWithoutEntry() {
        Map<String, Object> params = baseParams("50.00", "0", "NO2");
        when(userService.getByMobile("13800138000")).thenReturn(mockUser);
        when(baseMapper.selectCount(any())).thenReturn(0);
        when(baseMapper.insert(any(TransFlow.class))).thenReturn(1);

        assertTrue(transFlowService.handleWithdrawNotify(params));

        verify(baseMapper).insert(any(TransFlow.class));
    }
}
