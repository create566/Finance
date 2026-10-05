package com.example.demo.utils;

import com.example.demo.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 余额闸门单元测试。
 *
 * <p>{@link BalanceGuard} 是投标 / 还款 / 提现三条"真扣钱"路径的统一前置闸门。
 * 在它出现之前：还款与提现**根本没有这道校验**，余额为 0 也能提交到银行，
 * 银行扣不动、回调失败，而用户以为操作成功了。</p>
 *
 * <p>纯 JUnit，无任何依赖。锁定四件事：边界相等放行、文案格式、空前缀不重复、
 * 以及"差多少"这个数字必须算对（用户据此决定充多少）。</p>
 */
@DisplayName("余额闸门 BalanceGuard 测试")
class BalanceGuardTest {

    // ==================== require：够 / 不够 ====================

    @Test
    @DisplayName("余额充足 → 放行")
    void require_enough_passes() {
        assertDoesNotThrow(() ->
                BalanceGuard.require("提现", new BigDecimal("1000.00"), new BigDecimal("300.00")));
    }

    @Test
    @DisplayName("余额刚好等于所需 → 放行（边界是 >=，不是 >）")
    void require_exactlyEqual_passes() {
        assertDoesNotThrow(() ->
                BalanceGuard.require("提现", new BigDecimal("300.00"), new BigDecimal("300.00")));
    }

    @Test
    @DisplayName("余额差 0.01 → 拒绝（边界下方立刻拦截）")
    void require_shortByOneCent_throws() {
        assertThrows(BusinessException.class, () ->
                BalanceGuard.require("提现", new BigDecimal("299.99"), new BigDecimal("300.00")));
    }

    @Test
    @DisplayName("余额不足 → 400 + 文案含「本次需 / 当前可用 / 还差」三个数字")
    void require_insufficient_messageContainsAllFigures() {
        BusinessException ex = assertThrows(BusinessException.class, () ->
                BalanceGuard.require("提现", new BigDecimal("100.00"), new BigDecimal("300.00")));

        assertEquals(400, ex.getCode(), "余额不足应为业务错误码 400，不能落到 500");
        String msg = ex.getMessage();
        assertTrue(msg.contains("提现失败："), "应带动作名前缀，实际：" + msg);
        assertTrue(msg.contains("本次需 ¥300.00"), "实际：" + msg);
        assertTrue(msg.contains("当前可用 ¥100.00"), "实际：" + msg);
        assertTrue(msg.contains("还差 ¥200.00"), "差额必须算对（用户据此决定充多少），实际：" + msg);
    }

    @Test
    @DisplayName("actionName 传空串 → 不拼前缀（避免出现「投标失败：投标失败：」）")
    void require_blankActionName_noPrefix() {
        BusinessException ex = assertThrows(BusinessException.class, () ->
                BalanceGuard.require("", new BigDecimal("0"), new BigDecimal("50.00")));

        assertFalse(ex.getMessage().startsWith("失败："), "空动作名不应产生前缀，实际：" + ex.getMessage());
        assertTrue(ex.getMessage().startsWith("可用余额不足"), "实际：" + ex.getMessage());
    }

    @Test
    @DisplayName("actionName 传 null → 不拼前缀（不抛 NPE）")
    void require_nullActionName_noPrefixNoNpe() {
        BusinessException ex = assertThrows(BusinessException.class, () ->
                BalanceGuard.require(null, BigDecimal.ZERO, new BigDecimal("1.00")));

        assertTrue(ex.getMessage().startsWith("可用余额不足"), "实际：" + ex.getMessage());
    }

    @Test
    @DisplayName("余额为 null → 按 0 处理并正常拦截（不抛 NPE）")
    void require_nullAvailable_treatedAsZero() {
        BusinessException ex = assertThrows(BusinessException.class, () ->
                BalanceGuard.require("还款", null, new BigDecimal("0.01")));

        assertTrue(ex.getMessage().contains("当前可用 ¥0.00"), "实际：" + ex.getMessage());
    }

    @Test
    @DisplayName("两边都为 null/0 → 放行（0 需 0 是够的）")
    void require_zeroNeedZeroHave_passes() {
        assertDoesNotThrow(() -> BalanceGuard.require("还款", null, BigDecimal.ZERO));
    }

    @Test
    @DisplayName("金额统一按两位小数 HALF_UP 归一后再比较")
    void require_scaleNormalizedBeforeCompare() {
        // 1.005 → 1.01；1.01 够 1.01 → 放行
        assertDoesNotThrow(() ->
                BalanceGuard.require("提现", new BigDecimal("1.01"), new BigDecimal("1.005")));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                BalanceGuard.require("提现", new BigDecimal("1.004"), new BigDecimal("1.005")));
        assertTrue(ex.getMessage().contains("本次需 ¥1.01"), "应归一为 1.01，实际：" + ex.getMessage());
    }

    // ==================== requirePositive ====================

    @Test
    @DisplayName("正数且两位小数以内 → 放行")
    void requirePositive_valid_passes() {
        assertDoesNotThrow(() -> BalanceGuard.requirePositive(new BigDecimal("0.01"), "提现"));
        assertDoesNotThrow(() -> BalanceGuard.requirePositive(new BigDecimal("100"), "提现"));
        assertDoesNotThrow(() -> BalanceGuard.requirePositive(new BigDecimal("99.99"), "提现"));
    }

    @Test
    @DisplayName("0 / 负数 / null → 拒绝")
    void requirePositive_nonPositive_throws() {
        assertThrows(BusinessException.class, () ->
                BalanceGuard.requirePositive(BigDecimal.ZERO, "提现"));
        assertThrows(BusinessException.class, () ->
                BalanceGuard.requirePositive(new BigDecimal("-1"), "提现"));
        assertThrows(BusinessException.class, () ->
                BalanceGuard.requirePositive(null, "提现"));
    }

    @Test
    @DisplayName("超过两位小数 → 拒绝（钱不能有厘）")
    void requirePositive_tooManyDecimals_throws() {
        BusinessException ex = assertThrows(BusinessException.class, () ->
                BalanceGuard.requirePositive(new BigDecimal("1.001"), "提现"));

        assertTrue(ex.getMessage().contains("最多保留两位小数"), "实际：" + ex.getMessage());
    }
}
