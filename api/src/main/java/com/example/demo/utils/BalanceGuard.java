package com.example.demo.utils;

import com.example.demo.exception.BusinessException;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 余额闸门：所有「从用户可用余额里真扣钱」的动作（投标 / 还款 / 提现）都要先过这里。
 *
 * 为什么单独抽一个类：这三个入口原本各写各的，文案从"余额不足"到
 * "账户余额不足或账户异常，操作失败"都有，用户端把 message 直接弹出来，
 * 同一件事三种说法。更重要的是 —— 还款与提现原先**根本没有这道闸门**，
 * 余额为 0 也能提交到银行，银行扣不动、回调失败，用户还以为自己操作成功了。
 *
 * 这里只做"够不够"的判定，不做余额变更；变更仍由
 * {@code UserAccountService.updateBalance} 的原子 SQL 兜底（防并发）。
 */
public final class BalanceGuard {

    private BalanceGuard() {
    }

    /**
     * 校验可用余额是否够本次扣减，不够直接抛业务异常。
     *
     * @param actionName 动作名，会拼成「XX失败：…」；传空串则由调用方自己加前缀
     *                   （例如 LendController 已经统一加了"投标失败："，这里再带一遍会重复）
     * @param available  当前可用余额（来自银行，平台不持有余额）
     * @param required   本次需要扣减的金额
     */
    public static void require(String actionName, BigDecimal available, BigDecimal required) {
        BigDecimal have = scale(available);
        BigDecimal need = scale(required);
        if (have.compareTo(need) >= 0) {
            return;
        }
        String prefix = (actionName == null || actionName.trim().isEmpty()) ? "" : actionName.trim() + "失败：";
        throw new BusinessException(400, prefix + String.format(
                "可用余额不足，本次需 ¥%s，当前可用 ¥%s，还差 ¥%s，请先充值。",
                need.toPlainString(), have.toPlainString(),
                need.subtract(have).toPlainString()));
    }

    /**
     * 金额必须为正数且最多两位小数。
     */
    public static void requirePositive(BigDecimal amount, String actionName) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(400, actionName + "金额必须大于 0");
        }
        if (amount.scale() > 2) {
            throw new BusinessException(400, actionName + "金额最多保留两位小数");
        }
    }

    private static BigDecimal scale(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }
}
