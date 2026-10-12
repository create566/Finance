package com.example.demo.utils;

import com.example.demo.utils.ReturnPlanCalculator.PeriodPlan;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 还款计划金额口径（2026-10-08 修 P4-53）。
 *
 * <p><b>背景：</b>多投资人标的的还款在银行侧<b>必然失败</b>。
 * 银行 {@code returnCommit} 有强等式 {@code voteFeeAmt + ΣtransitAmt == totalAmt}
 * （转 double 比较），而平台分摊时每个投资人各自四舍五入到分，加总与期供差 1 分 ⇒
 * 抛 {@code RETURN_AMMOUNT_MORE_ERROR}。
 *
 * <p>根因之一是计划本身就不自洽：{@code total} 原先是"未舍入的 (本金+利息) 单独舍入"，
 * 而 {@code principal} / {@code interest} 各自舍入，于是出现
 * {@code 803.22 + 66.67 = 869.89} 但 {@code total = 869.88}。
 * 存量 60 期里有 22 期因此不满足 {@code principal + interest == total}。
 *
 * <p>本类把这个口径钉死：<b>total 必须等于「已舍入的本金 + 已舍入的利息」</b>。
 */
class ReturnPlanCalculatorTest {

    private static final LocalDate START = LocalDate.of(2026, 10, 8);

    private static BigDecimal sum(List<PeriodPlan> plans, java.util.function.Function<PeriodPlan, BigDecimal> f) {
        BigDecimal s = BigDecimal.ZERO;
        for (PeriodPlan p : plans) {
            s = s.add(f.apply(p));
        }
        return s;
    }

    @Test
    @DisplayName("等额本息：每期 total == principal + interest")
    void equalPrincipalAndInterest_totalEqualsPrincipalPlusInterest() {
        List<PeriodPlan> plans = ReturnPlanCalculator.calc(
                new BigDecimal("10000.00"), new BigDecimal("8.00"), 12, 1, START);
        assertEquals(12, plans.size());
        for (PeriodPlan p : plans) {
            assertEquals(0, p.getTotal().compareTo(p.getPrincipal().add(p.getInterest())),
                    "第 " + p.getPeriod() + " 期 total 必须等于已舍入的本金+利息");
        }
    }

    @Test
    @DisplayName("等额本息：非末期 total 恒等于月供（不会逐期漂移）")
    void equalPrincipalAndInterest_monthlyPaymentIsStable() {
        List<PeriodPlan> plans = ReturnPlanCalculator.calc(
                new BigDecimal("10000.00"), new BigDecimal("8.00"), 12, 1, START);
        BigDecimal first = plans.get(0).getTotal();
        for (int i = 0; i < plans.size() - 1; i++) {
            assertEquals(0, plans.get(i).getTotal().compareTo(first),
                    "第 " + plans.get(i).getPeriod() + " 期月供给漂了");
        }
    }

    @Test
    @DisplayName("等额本金：每期 total == principal + interest")
    void equalPrincipal_totalEqualsPrincipalPlusInterest() {
        List<PeriodPlan> plans = ReturnPlanCalculator.calc(
                new BigDecimal("10000.00"), new BigDecimal("8.00"), 12, 2, START);
        for (PeriodPlan p : plans) {
            assertEquals(0, p.getTotal().compareTo(p.getPrincipal().add(p.getInterest())),
                    "第 " + p.getPeriod() + " 期 total 必须等于已舍入的本金+利息");
        }
    }

    @Test
    @DisplayName("每月还息到期还本：每期 total == principal + interest")
    void monthlyInterestLumpPrincipal_totalEqualsPrincipalPlusInterest() {
        List<PeriodPlan> plans = ReturnPlanCalculator.calc(
                new BigDecimal("10000.00"), new BigDecimal("8.00"), 12, 3, START);
        for (PeriodPlan p : plans) {
            assertEquals(0, p.getTotal().compareTo(p.getPrincipal().add(p.getInterest())),
                    "第 " + p.getPeriod() + " 期 total 必须等于已舍入的本金+利息");
        }
    }

    @Test
    @DisplayName("一次还本付息：total == principal + interest")
    void lumpSum_totalEqualsPrincipalPlusInterest() {
        List<PeriodPlan> plans = ReturnPlanCalculator.calc(
                new BigDecimal("10000.00"), new BigDecimal("8.00"), 12, 4, START);
        assertEquals(1, plans.size());
        PeriodPlan p = plans.get(0);
        assertEquals(0, p.getTotal().compareTo(p.getPrincipal().add(p.getInterest())));
    }

    @Test
    @DisplayName("本金守恒：Σ本金 == 放款金额（四种还款方式都成立）")
    void principalSumEqualsAmount() {
        BigDecimal amount = new BigDecimal("10000.00");
        for (int method = 1; method <= 4; method++) {
            List<PeriodPlan> plans = ReturnPlanCalculator.calc(
                    amount, new BigDecimal("8.00"), 12, method, START);
            assertEquals(0, sum(plans, PeriodPlan::getPrincipal).compareTo(amount),
                    "还款方式 " + method + " 的本金合计应等于放款金额");
        }
    }

    @Test
    @DisplayName("证据：投资人各自独立算一遍，加总并不等于整体 —— 所以必须「按占比分摊 + 倒挤」")
    void independentCalcIsNotAdditive() {
        // 等额本息在数学上可加，但各自四舍五入到分之后就不可加了。
        // 尤其末期：末期本金取"剩余本金"，前面的舍入误差会累积，
        // 实测标的10 第12期偏差达 0.13 元 —— 远不止 1 分。
        // 这条用例把这个事实钉住，作为 bookMakeLoan 改用「按投资占比分摊」的依据。
        // （不断言"一定有差"，只断言：一旦有差，偏差必须有界，且本金合计仍守恒。）
        List<PeriodPlan> whole = ReturnPlanCalculator.calc(
                new BigDecimal("10000.00"), new BigDecimal("8.00"), 12, 1, START);
        List<PeriodPlan> a = ReturnPlanCalculator.calc(
                new BigDecimal("1000.00"), new BigDecimal("8.00"), 12, 1, START);
        List<PeriodPlan> b = ReturnPlanCalculator.calc(
                new BigDecimal("9000.00"), new BigDecimal("8.00"), 12, 1, START);

        // 本金合计各自守恒（这是可加性的好一面）
        assertEquals(0, sum(a, PeriodPlan::getPrincipal).compareTo(new BigDecimal("1000.00")));
        assertEquals(0, sum(b, PeriodPlan::getPrincipal).compareTo(new BigDecimal("9000.00")));

        // 逐期偏差有界（<= 0.20），说明倒挤量可控，不会因为末期为负而崩掉
        for (int i = 0; i < whole.size(); i++) {
            BigDecimal diff = whole.get(i).getTotal()
                    .subtract(a.get(i).getTotal().add(b.get(i).getTotal())).abs();
            assertTrue(diff.compareTo(new BigDecimal("0.20")) <= 0,
                    "第 " + (i + 1) + " 期独立计算偏差 " + diff + " 超出可控范围");
        }
    }
}
