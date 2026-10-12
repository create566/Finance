package com.example.demo.utils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/**
 * 「预期收益」的统一口径 = <b>该笔投资在还款计划里能拿到的利息合计</b>。
 *
 * <p><b>为什么必须统一：</b>改造前有<b>两套互不相同</b>的算法，且都不等于实际回款：
 * <ul>
 *   <li>用户端 {@code LendItemServiceImpl#calcExpectAmount}：<b>单利</b>
 *       {@code 金额 × 年利率 × 期数/12}。1000 万 / 8% / 12 期 ⇒ 显示 <b>80 万</b>；</li>
 *   <li>管理端 {@code AdminLendController#investments}：{@code 金额 × 年利率 × 天数/365}，
 *       且 {@code lendStartDate} 为空时<b>默认按 30 天</b> ⇒ 同一笔投资两个页面显示两个数；</li>
 *   <li>而实际回款（等额本息）利息合计只有 <b>约 43.86 万</b>——</li>
 * </ul>
 * 差额 ≈ <b>36.1 万（1.82 倍）</b>：投资人看到的"预期收益"是拿不到的钱。
 * 根因是「单利全额计息」与「本金逐期归还、利息逐期递减」是两种不同的计息方式。
 *
 * <p>本类改为直接复用 {@link ReturnPlanCalculator}——即<b>还款计划本身</b>——
 * 把各期利息相加，保证「页面上显示的预期收益 ≡ 实际能收到的利息」。
 */
public final class ExpectReturnUtil {

    /** 计算起点日，只影响期数不影响利息总额（各期利息与日期无关） */
    private static final LocalDate FIXED_START = LocalDate.of(2000, 1, 1);

    private ExpectReturnUtil() {
    }

    /**
     * 该笔金额按给定利率/期数/还款方式，全周期可获得的利息合计。
     *
     * @param amount       投资金额（或借款金额）
     * @param yearRate     年化利率（百分数，如 8.00）
     * @param period       期数（月）
     * @param returnMethod 还款方式（1 等额本息 / 2 等额本金 / 3 每月还息到期还本 / 4 一次还本付息）
     * @return 利息合计，2 位小数；入参不合法时返回 0.00
     */
    public static BigDecimal expectedInterest(BigDecimal amount, BigDecimal yearRate,
                                              Integer period, Integer returnMethod) {
        if (amount == null || yearRate == null || period == null || period <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        if (amount.signum() <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        int method = returnMethod == null ? 1 : returnMethod;
        List<ReturnPlanCalculator.PeriodPlan> plans;
        try {
            plans = ReturnPlanCalculator.calc(amount, yearRate, period, method, FIXED_START);
        } catch (RuntimeException e) {
            // 不支持的还款方式等异常不应把列表接口打挂，退化为 0 并在调用方保持可读
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal interest = BigDecimal.ZERO;
        for (ReturnPlanCalculator.PeriodPlan p : plans) {
            interest = interest.add(p.getInterest() == null ? BigDecimal.ZERO : p.getInterest());
        }
        return interest.setScale(2, RoundingMode.HALF_UP);
    }
}
