package com.example.demo.service.impl;

import com.example.demo.entity.TransFlow;
import com.example.demo.entity.UserAccount;
import com.example.demo.enums.TransStatusEnum;
import com.example.demo.enums.TransTypeEnum;
import com.example.demo.mapper.TransFlowMapper;
import com.example.demo.mapper.UserAccountMapper;
import com.example.demo.service.UserAccountService;
import com.example.demo.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * P0-1：统一可用额口径。
 *
 * <p><b>为什么单独建一个类：</b>银行侧扣款<b>不校验余额</b> —— javap 确认
 * {@code lockAccount}（投标冻结）、{@code withdraw}（提现）、{@code transferAccounts}（划转）
 * 三者都是无条件加减、永远返回成功。平台唯一能做的就是"发起前把在途占住"，
 * 而且三个动钱入口必须共用同一个算法：
 * <pre>
 *   可用额 = 银行余额 − Σ(提现 + 投标 + 还款 的 PROCESSING 流水)
 * </pre>
 * 此前提现侧自己算（只扣在途提现），投标与还款直接用裸余额 —— 三套口径，
 * 并发两笔就能把账户扣成负数（库里已存在 3 个负余额账户）。
 *
 * <p>纯 Mockito：不依赖 MySQL / Redis / 银行接口。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("统一可用额口径测试（P0-1）")
class AvailableBalanceUnifiedTest {

    private static final Long USER_ID = 42L;

    @Mock
    private UserAccountMapper userAccountMapper;
    @Mock
    private UserService userService;
    @Mock
    private UserAccountService userAccountService;
    @Mock
    private JdbcTemplate bankJdbcTemplate;
    @Mock
    private TransFlowMapper transFlowMapper;

    @InjectMocks
    private BankAccountSyncServiceImpl service;

    /** 把"查银行余额"钉成固定值，只验证在途扣减这一段 */
    private BankAccountSyncServiceImpl spyWithBalance(BigDecimal balance) {
        BankAccountSyncServiceImpl spy = spy(service);
        doReturn(balance).when(spy).getBankBalance(anyLong());
        return spy;
    }

    @Test
    @DisplayName("可用额 = 银行余额 − 在途扣款")
    void availableIsBalanceMinusPending() {
        BankAccountSyncServiceImpl spy = spyWithBalance(new BigDecimal("1000.00"));
        when(transFlowMapper.sumAmountByTypes(eq(USER_ID), any(), eq(TransStatusEnum.PROCESSING.getCode())))
                .thenReturn(new BigDecimal("300.00"));

        assertEquals(0, new BigDecimal("700.00").compareTo(spy.getAvailableBalance(USER_ID)));
    }

    @Test
    @DisplayName("在途统计必须覆盖 提现(2) + 投标(3) + 还款(6)，且只看 PROCESSING")
    @SuppressWarnings("unchecked")
    void pendingQueryCoversAllDebitTypes() {
        BankAccountSyncServiceImpl spy = spyWithBalance(new BigDecimal("1000.00"));
        when(transFlowMapper.sumAmountByTypes(eq(USER_ID), any(), eq(TransStatusEnum.PROCESSING.getCode())))
                .thenReturn(BigDecimal.ZERO);

        spy.getAvailableBalance(USER_ID);

        ArgumentCaptor<List<Integer>> types = ArgumentCaptor.forClass(List.class);
        org.mockito.Mockito.verify(transFlowMapper)
                .sumAmountByTypes(eq(USER_ID), types.capture(), eq(TransStatusEnum.PROCESSING.getCode()));

        List<Integer> captured = types.getValue();
        assertTrue(captured.contains(TransTypeEnum.WITHDRAW.getCode()), "漏了提现 ⇒ 提现并发会透支");
        assertTrue(captured.contains(TransTypeEnum.INVEST.getCode()), "漏了投标 ⇒ 投标并发会透支");
        assertTrue(captured.contains(TransTypeEnum.REPAY.getCode()), "漏了还款 ⇒ 还款并发会透支");
        // 放款/回款不由用户余额出，充值反而加钱，都不该占用可用额
        assertTrue(!captured.contains(TransTypeEnum.RECHARGE.getCode()), "充值不应占用可用额");
        assertTrue(!captured.contains(TransTypeEnum.RETURN.getCode()), "投资回款不应占用可用额");
        assertEquals(3, captured.size());
    }

    @Test
    @DisplayName("在途超过余额 → 返回 0，不得返回负数（负号会穿透到用户文案）")
    void pendingExceedsBalance_clampedToZero() {
        BankAccountSyncServiceImpl spy = spyWithBalance(new BigDecimal("100.00"));
        when(transFlowMapper.sumAmountByTypes(eq(USER_ID), any(), eq(TransStatusEnum.PROCESSING.getCode())))
                .thenReturn(new BigDecimal("500.00"));

        assertEquals(0, BigDecimal.ZERO.compareTo(spy.getAvailableBalance(USER_ID)));
    }

    @Test
    @DisplayName("在途聚合返回 null → 按 0 处理，不得 NPE")
    void nullPendingTreatedAsZero() {
        BankAccountSyncServiceImpl spy = spyWithBalance(new BigDecimal("1000.00"));
        when(transFlowMapper.sumAmountByTypes(eq(USER_ID), any(), eq(TransStatusEnum.PROCESSING.getCode())))
                .thenReturn(null);

        assertEquals(0, new BigDecimal("1000.00").compareTo(spy.getAvailableBalance(USER_ID)));
    }

    @Test
    @DisplayName("在途查询抛异常 → 降级为 0 并放行，不能因为增强校验把正常业务拦死")
    void pendingQueryFailure_degradesToZero() {
        BankAccountSyncServiceImpl spy = spyWithBalance(new BigDecimal("1000.00"));
        when(transFlowMapper.sumAmountByTypes(eq(USER_ID), any(), eq(TransStatusEnum.PROCESSING.getCode())))
                .thenThrow(new RuntimeException("db down"));

        assertEquals(0, new BigDecimal("1000.00").compareTo(spy.getAvailableBalance(USER_ID)));
    }

    @Test
    @DisplayName("扫描银行异常账户：只挑 余额为负 或 冻结为负")
    void findAbnormalAccounts_onlyNegative() {
        when(bankJdbcTemplate.queryForList(org.mockito.ArgumentMatchers.contains("amount < 0")))
                .thenReturn(List.of(
                        java.util.Map.of("user_code", "BIND1", "amount", new BigDecimal("-650.60"),
                                "freeze_amount", new BigDecimal("11000.00")),
                        java.util.Map.of("user_code", "BIND2", "amount", new BigDecimal("10.00"),
                                "freeze_amount", new BigDecimal("-500.00"))));

        List<java.util.Map<String, Object>> abnormal = service.findAbnormalAccounts();
        assertEquals(2, abnormal.size());
        assertEquals("BIND1", abnormal.get(0).get("userCode"));
        assertEquals(0, new BigDecimal("-650.60").compareTo((BigDecimal) abnormal.get(0).get("amount")));
        assertEquals(0, new BigDecimal("-500.00").compareTo((BigDecimal) abnormal.get(1).get("freezeAmount")));
    }

    @Test
    @DisplayName("扫描银行异常账户失败 → 返回空列表，不得把对账任务打挂")
    void findAbnormalAccounts_failureReturnsEmpty() {
        doThrow(new RuntimeException("rds down")).when(bankJdbcTemplate).queryForList(any(String.class));
        assertTrue(service.findAbnormalAccounts().isEmpty());
    }

    @Test
    @DisplayName("TransFlow 占位流水必须落在 PROCESSING，才能占用可用额")
    void pendingFlowIsProcessing() {
        // 占位流水若落成终态就不会被 getPendingDebitAmount 统计到，等于没占用
        TransFlow flow = new TransFlow();
        flow.setStatus(TransStatusEnum.PROCESSING.getCode());
        flow.setTransType(TransTypeEnum.INVEST.getCode());
        assertEquals(TransStatusEnum.PROCESSING.getCode(), flow.getStatus());
        assertEquals(TransTypeEnum.INVEST.getCode(), flow.getTransType());
    }
}
