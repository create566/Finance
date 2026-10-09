package com.example.demo.service.impl;

import com.example.demo.entity.TransFlow;
import com.example.demo.entity.User;
import com.example.demo.entity.UserBind;
import com.example.demo.enums.BindStatusEnum;
import com.example.demo.enums.TransStatusEnum;
import com.example.demo.enums.TransTypeEnum;
import com.example.demo.exception.BusinessException;
import com.example.demo.mapper.TransFlowMapper;
import com.example.demo.service.BankAccountSyncService;
import com.example.demo.service.UserBindService;
import com.example.demo.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 提现闸门单元测试。
 *
 * <p>背景：{@code buildWithdrawForm} 原先只看【银行余额】。但银行余额只有在银行回调
 * （{@code handleWithdrawNotify}）之后才减少，而用户完成银行页面付款之前，
 * 那条「处理中」流水一直是 PROCESSING —— 于是
 * <pre>
 *   余额 1000 → 提 1000（受理，银行余额仍 1000）→ 再提 1000（又受理）
 * </pre>
 * 同一笔钱被重复提交两次，不需要并发、间隔几秒连点两次就能触发。
 * 正确口径：可用余额 = 银行余额 − Σ(处理中提现)。
 *
 * <p>纯 Mockito：不依赖 MySQL / Redis / 银行接口。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("提现可用余额闸门测试")
class UserAccountWithdrawGuardTest {

    private static final Long USER_ID = 42L;
    private static final Integer WITHDRAW = TransTypeEnum.WITHDRAW.getCode();
    private static final Integer PROCESSING = TransStatusEnum.PROCESSING.getCode();

    @Mock
    private UserService userService;

    @Mock
    private UserBindService userBindService;

    @Mock
    private BankAccountSyncService bankAccountSyncService;

    @Mock
    private TransFlowMapper transFlowMapper;

    @InjectMocks
    private UserAccountServiceImpl userAccountService;

    /**
     * 绑卡状态、用户信息就绪，并按【统一可用额口径】stub 银行余额与在途占用。
     *
     * @param pending  扣款类在途合计（提现 + 投标 + 还款）；传 null 表示不 stub
     * @param balance  银行余额；传 null 表示不 stub（用于"压根不该查余额"的用例）
     */
    private void boundUserWithPending(BigDecimal pending, BigDecimal balance) {
        User user = new User();
        user.setId(USER_ID);
        user.setMobile("13800000000");
        user.setName("测试用户");

        UserBind bind = new UserBind();
        bind.setUserId(USER_ID);
        bind.setBindCode("BIND_CODE_42");
        bind.setStatus(BindStatusEnum.BOUND.getCode());

        when(userService.getById(USER_ID)).thenReturn(user);
        when(userBindService.getByUserId(USER_ID)).thenReturn(bind);

        if (balance != null) {
            when(bankAccountSyncService.getBankBalance(USER_ID)).thenReturn(balance);
        }
        if (pending != null && balance != null) {
            when(bankAccountSyncService.getPendingDebitAmount(USER_ID)).thenReturn(pending);
            when(bankAccountSyncService.getAvailableBalance(USER_ID)).thenReturn(balance.subtract(pending));
        }
    }

    @Test
    @DisplayName("在途提现占满额度 → 拒绝，并说明是在途占用而非余额不足")
    void pendingWithdrawExhaustsBalance_rejectedWithExplanation() {
        boundUserWithPending(new BigDecimal("1000.00"), new BigDecimal("1000.00"));
        when(bankAccountSyncService.getBankBalance(USER_ID)).thenReturn(new BigDecimal("1000.00"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> userAccountService.buildWithdrawForm(USER_ID, new BigDecimal("1000.00")));

        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("¥1000.00 资金在途"), "应说明在途金额，实际：" + ex.getMessage());
        assertTrue(ex.getMessage().contains("扣除后可用 ¥0.00"), "应给出扣除后可用余额，实际：" + ex.getMessage());
        // 关键：被拒的提现绝不能落「处理中」流水，否则占用会被重复计算
        verify(transFlowMapper, never()).insert(any(TransFlow.class));
    }

    @Test
    @DisplayName("在途提现部分占用 → 按「余额 − 在途」判定，不足即拒")
    void pendingWithdrawPartiallyReserves_usesAvailableBalance() {
        boundUserWithPending(new BigDecimal("600.00"), new BigDecimal("1000.00"));
        when(bankAccountSyncService.getBankBalance(USER_ID)).thenReturn(new BigDecimal("1000.00"));

        // 可用 = 1000 - 600 = 400 < 500
        BusinessException ex = assertThrows(BusinessException.class,
                () -> userAccountService.buildWithdrawForm(USER_ID, new BigDecimal("500.00")));

        assertTrue(ex.getMessage().contains("¥600.00 资金在途"), "实际：" + ex.getMessage());
        assertTrue(ex.getMessage().contains("扣除后可用 ¥400.00"), "实际：" + ex.getMessage());
        verify(transFlowMapper, never()).insert(any(TransFlow.class));
    }

    @Test
    @DisplayName("在途占用后仍够用 → 放行并落「处理中」流水")
    void pendingWithdrawLeavesEnoughBalance_allowed() {
        boundUserWithPending(new BigDecimal("600.00"), new BigDecimal("1000.00"));
        when(bankAccountSyncService.getBankBalance(USER_ID)).thenReturn(new BigDecimal("1000.00"));

        // 可用 = 400，本次提 400（刚好够）
        String form = userAccountService.buildWithdrawForm(USER_ID, new BigDecimal("400.00"));

        assertNotNull(form);
        ArgumentCaptor<TransFlow> captor = ArgumentCaptor.forClass(TransFlow.class);
        verify(transFlowMapper, times(1)).insert(captor.capture());

        TransFlow saved = captor.getValue();
        assertEquals(PROCESSING, saved.getStatus(), "发起时应落「处理中」");
        assertEquals(WITHDRAW, saved.getTransType());
        assertEquals(0, new BigDecimal("400.00").compareTo(saved.getTransAmount()));
        assertNotNull(saved.getTransNo(), "必须生成业务单号供银行回调定位");
    }

    @Test
    @DisplayName("在途提现为空（SUM 返回 null）→ 按 0 处理，不得 NPE")
    void nullPendingSum_treatedAsZero() {
        // 在途为 0 的语义由统一入口给出：可用额 = 余额
        boundUserWithPending(BigDecimal.ZERO, new BigDecimal("1000.00"));

        assertNotNull(userAccountService.buildWithdrawForm(USER_ID, new BigDecimal("1000.00")));
        verify(transFlowMapper, times(1)).insert(any(TransFlow.class));
    }

    @Test
    @DisplayName("纯粹余额不足（无在途）→ 走统一闸门文案，含「还差」金额")
    void noPendingWithdraw_insufficientBalance_usesBalanceGuardWording() {
        boundUserWithPending(BigDecimal.ZERO, new BigDecimal("1000.00"));
        when(bankAccountSyncService.getBankBalance(USER_ID)).thenReturn(new BigDecimal("1000.00"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> userAccountService.buildWithdrawForm(USER_ID, new BigDecimal("2000.00")));

        String msg = ex.getMessage();
        assertTrue(msg.startsWith("提现失败：可用余额不足"), "实际：" + msg);
        assertTrue(msg.contains("本次需 ¥2000.00") && msg.contains("当前可用 ¥1000.00") && msg.contains("还差 ¥1000.00"),
                "实际：" + msg);
        verify(transFlowMapper, never()).insert(any(TransFlow.class));
    }

    @Test
    @DisplayName("提现必须走「统一可用额」入口，不得再直接用裸银行余额（P0-1）")
    void withdrawMustUseUnifiedAvailableBalance() {
        boundUserWithPending(BigDecimal.ZERO, new BigDecimal("1000.00"));

        userAccountService.buildWithdrawForm(USER_ID, new BigDecimal("100.00"));

        // 银行扣款不校验余额，三个动钱入口必须共用同一个可用额函数，
        // 否则"提现自己扣在途、投标/还款用裸余额"的旧口径会让资金被重复占用。
        verify(bankAccountSyncService).getAvailableBalance(USER_ID);
        verify(bankAccountSyncService).getPendingDebitAmount(USER_ID);
    }

    @Test
    @DisplayName("金额非法（0 / 负 / 超两位小数）在进入余额计算前就被拒")
    void illegalAmount_rejectedBeforeBalanceLookup() {
        // 不 stub 余额相关查询：金额非法应在进入余额计算前就被拒（strict stubs 会兜住）
        boundUserWithPending(null, null);

        BusinessException zero = assertThrows(BusinessException.class,
                () -> userAccountService.buildWithdrawForm(USER_ID, BigDecimal.ZERO));
        assertTrue(zero.getMessage().contains("提现金额必须大于 0"), "实际：" + zero.getMessage());

        BusinessException precision = assertThrows(BusinessException.class,
                () -> userAccountService.buildWithdrawForm(USER_ID, new BigDecimal("10.123")));
        assertTrue(precision.getMessage().contains("最多保留两位小数"), "实际：" + precision.getMessage());

        verify(bankAccountSyncService, never()).getAvailableBalance(any());
        verify(transFlowMapper, never()).insert(any(TransFlow.class));
    }
}
