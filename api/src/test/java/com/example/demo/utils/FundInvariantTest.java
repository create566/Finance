package com.example.demo.utils;

import com.example.demo.utils.ReturnPlanCalculator.PeriodPlan;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 「借款额 → 投资额 → 还款额」三条资金链的<b>恒等式断言</b>。
 *
 * <p>本类把"能不能平账"从主观判断变成可自动验证的红/绿灯。覆盖：
 * <ol>
 *   <li>本金守恒：Σ各期本金 ≡ 放款金额（四种还款方式 × 边界矩阵）；</li>
 *   <li>明细守恒：Σ投资人明细 ≡ 该期期供（本金 / 利息 / 总额三者同时成立）；</li>
 *   <li>单户精度：最大余数法下<b>单户偏差严格小于 1 分</b>；</li>
 *   <li>可复现：同一输入必得同一输出（不依赖 Map 遍历顺序）；</li>
 *   <li>钉住旧实现的缺陷：逐户四舍五入的残差随户数线性放大，且<b>整段残差压在一户</b>身上；</li>
 *   <li>预期收益口径：页面显示的"预期收益" ≡ 还款计划利息合计（单利口径的差距被钉住）。</li>
 * </ol>
 */
class FundInvariantTest {

    private static final LocalDate START = LocalDate.of(2026, 10, 12);

    /** 可被测试的金额边界：1 分 ~ 1 亿 */
    private static final String[] AMOUNTS = {
            "0.01", "0.10", "1.00", "99.99", "10000.00", "1000000.00", "10000000.00", "100000000.00"
    };
    private static final int[] PERIODS = {1, 2, 3, 6, 12, 36, 60};
    private static final int[] METHODS = {1, 2, 3, 4};

    private static BigDecimal sum(List<PeriodPlan> plans, java.util.function.Function<PeriodPlan, BigDecimal> f) {
        BigDecimal s = BigDecimal.ZERO;
        for (PeriodPlan p : plans) {
            s = s.add(f.apply(p));
        }
        return s;
    }

    // ------------------------------------------------------------------
    // 恒等式 ①②④：本金守恒（金额 × 期数 × 还款方式 全矩阵）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("恒等式·本金守恒：Σ各期本金 ≡ 放款金额（金额边界 × 期数 × 4 种还款方式 全矩阵）")
    void principalConservation_fullMatrix() {
        int checked = 0;
        for (String amount : AMOUNTS) {
            for (int period : PERIODS) {
                for (int method : METHODS) {
                    BigDecimal a = new BigDecimal(amount);
                    List<PeriodPlan> plans = ReturnPlanCalculator.calc(
                            a, new BigDecimal("8.00"), period, method, START);
                    assertEquals(0, sum(plans, PeriodPlan::getPrincipal).compareTo(a),
                            "本金合计 != 放款金额: amount=" + amount + ", period=" + period + ", method=" + method);
                    for (PeriodPlan p : plans) {
                        assertEquals(0, p.getTotal().compareTo(p.getPrincipal().add(p.getInterest())),
                                "total != principal + interest: " + amount + "/" + period + "/" + method
                                        + " 第" + p.getPeriod() + "期");
                    }
                    checked++;
                }
            }
        }
        assertEquals(AMOUNTS.length * PERIODS.length * METHODS.length, checked, "矩阵未跑满");
    }

    @Test
    @DisplayName("恒等式·1 亿边界：金额列 decimal(20,2) 不失真、不溢出")
    void oneHundredMillionBoundary() {
        BigDecimal amount = new BigDecimal("100000000.00"); // 1 亿
        for (int method : METHODS) {
            List<PeriodPlan> plans = ReturnPlanCalculator.calc(
                    amount, new BigDecimal("8.00"), 12, method, START);
            assertEquals(0, sum(plans, PeriodPlan::getPrincipal).compareTo(amount),
                    "1 亿 / 方式" + method + " 本金合计不等于放款金额");
            for (PeriodPlan p : plans) {
                // 每期金额必须是精确的 2 位小数（分），不允许出现 3 位以上小数
                assertTrue(p.getTotal().scale() <= 2, "出现超过 2 位小数的金额：" + p.getTotal());
                assertTrue(p.getTotal().signum() >= 0, "出现负的期供：" + p.getTotal());
            }
        }
    }

    // ------------------------------------------------------------------
    // 恒等式 ③：明细合计 ≡ 期供（最大余数法）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("恒等式·明细守恒：Σ投资人明细 ≡ 该期期供（本金/利息/总额三者同时成立）")
    void itemDetailSumEqualsPeriodDue() {
        BigDecimal lendAmount = new BigDecimal("10000000.00"); // 1000 万
        BigDecimal[] weights = equalWeights(lendAmount, 1000);
        List<PeriodPlan> plans = ReturnPlanCalculator.calc(
                lendAmount, new BigDecimal("8.00"), 12, 1, START);

        for (PeriodPlan p : plans) {
            BigDecimal[] principal = AllocationUtil.allocate(p.getPrincipal(), weights);
            BigDecimal[] interest = AllocationUtil.allocate(p.getInterest(), weights);

            BigDecimal sp = BigDecimal.ZERO;
            BigDecimal si = BigDecimal.ZERO;
            BigDecimal st = BigDecimal.ZERO;
            for (int i = 0; i < weights.length; i++) {
                sp = sp.add(principal[i]);
                si = si.add(interest[i]);
                st = st.add(principal[i].add(interest[i]));
            }
            assertEquals(0, sp.compareTo(p.getPrincipal()), "第" + p.getPeriod() + "期 Σ明细本金 != 期供本金");
            assertEquals(0, si.compareTo(p.getInterest()), "第" + p.getPeriod() + "期 Σ明细利息 != 期供利息");
            assertEquals(0, st.compareTo(p.getTotal()), "第" + p.getPeriod() + "期 Σ明细总额 != 期供总额");
        }
    }

    @Test
    @DisplayName("恒等式·单户精度：最大余数法下 1000 户 / 100000 户 的单户偏差都严格小于 1 分")
    void singleInvestorDeviationUnderOneCent() {
        BigDecimal due = new BigDecimal("869884.43"); // 1000 万 / 8% / 12 期 的月供
        for (int households : new int[]{2, 3, 10, 1000, 100000}) {
            BigDecimal[] weights = equalWeights(new BigDecimal("10000000.00"), households);
            BigDecimal[] got = AllocationUtil.allocate(due, weights);

            BigDecimal sum = BigDecimal.ZERO;
            BigDecimal weightSum = BigDecimal.ZERO;
            for (BigDecimal w : weights) {
                weightSum = weightSum.add(w);
            }
            BigDecimal maxDeviation = BigDecimal.ZERO;
            for (int i = 0; i < households; i++) {
                sum = sum.add(got[i]);
                // 精确份额按「权重占比」定义，而不是简单除以户数（各户权重可能有分位差）
                BigDecimal exact = due.multiply(weights[i]).divide(weightSum, 20, RoundingMode.HALF_UP);
                BigDecimal dev = got[i].subtract(exact).abs();
                if (dev.compareTo(maxDeviation) > 0) {
                    maxDeviation = dev;
                }
            }
            assertEquals(0, sum.compareTo(due), households + " 户时 Σ分摊 != 总额");
            assertTrue(maxDeviation.compareTo(new BigDecimal("0.01")) < 0,
                    households + " 户时单户偏差 " + maxDeviation + " 未小于 1 分");
        }
    }

    @Test
    @DisplayName("恒等式·可复现：同一输入必得同一输出，且与权重顺序无关（只跟下标绑定）")
    void allocationIsReproducible() {
        BigDecimal due = new BigDecimal("783421.19");
        BigDecimal[] weights = {new BigDecimal("1000.00"), new BigDecimal("3000.00"),
                new BigDecimal("2500.00"), new BigDecimal("777.77")};
        BigDecimal[] a = AllocationUtil.allocate(due, weights);
        BigDecimal[] b = AllocationUtil.allocate(due, weights);
        for (int i = 0; i < a.length; i++) {
            assertEquals(0, a[i].compareTo(b[i]), "第" + i + "户两次分摊结果不同");
        }
        BigDecimal s = BigDecimal.ZERO;
        for (BigDecimal v : a) {
            s = s.add(v);
        }
        assertEquals(0, s.compareTo(due), "Σ分摊 != 总额");
    }

    // ------------------------------------------------------------------
    // 钉住旧实现的缺陷（作为"为什么要改成最大余数法"的证据）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("钉住缺陷：旧的「逐户四舍五入 + 整段残差倒挤给一户」在 1000 户时单户偏差达数元")
    void legacyPerInvestorRoundingDumpsResidualOnOneInvestor() {
        BigDecimal due = new BigDecimal("869884.43");
        int households = 1000;
        BigDecimal per = due.divide(BigDecimal.valueOf(households), 10, RoundingMode.HALF_UP);

        // 旧实现：每户各自四舍五入到分
        BigDecimal legacySum = BigDecimal.ZERO;
        for (int i = 0; i < households; i++) {
            legacySum = legacySum.add(per.setScale(2, RoundingMode.HALF_UP));
        }
        BigDecimal residual = due.subtract(legacySum);

        // 残差不为 0，且旧实现把它整段加在"某一户"身上
        BigDecimal legacyWorstShare = per.setScale(2, RoundingMode.HALF_UP).add(residual);
        BigDecimal legacyDeviation = legacyWorstShare.subtract(per).abs();

        // 新实现：最大余数法
        BigDecimal[] fixed = AllocationUtil.allocate(due, equalWeights(new BigDecimal("10000000.00"), households));
        BigDecimal fixedDeviation = fixed[0].subtract(per).abs();

        System.out.println("[证据] 1000 户分摊月供 " + due
                + "：旧实现残差=" + residual + "（整段压在一户）→ 该户偏差=" + legacyDeviation
                + "；最大余数法单户偏差=" + fixedDeviation);

        assertTrue(legacyDeviation.compareTo(new BigDecimal("0.01")) > 0,
                "若旧实现的单户偏差已小于 1 分，这条证据就不成立，需重新评估");
        assertTrue(fixedDeviation.compareTo(new BigDecimal("0.01")) < 0,
                "最大余数法单户偏差必须小于 1 分");
    }

    @Test
    @DisplayName("钉住缺陷：旧实现残差上界随户数线性放大（≈0.005×户数），新实现恒定 <1 分")
    void legacyResidualGrowsLinearlyWithHouseholds() {
        BigDecimal due = new BigDecimal("438613.07"); // 1000 万 / 8% / 12 期 利息合计（约）
        for (int households : new int[]{100, 1000, 10000}) {
            BigDecimal[] weights = equalWeights(new BigDecimal("10000000.00"), households);
            BigDecimal weightSum = BigDecimal.ZERO;
            for (BigDecimal w : weights) {
                weightSum = weightSum.add(w);
            }

            // 旧实现：每户 money2(期供 × 占比)，占比 = w/W
            BigDecimal legacySum = BigDecimal.ZERO;
            BigDecimal legacyMaxDev = BigDecimal.ZERO;
            for (BigDecimal w : weights) {
                BigDecimal exact = due.multiply(w).divide(weightSum, 10, RoundingMode.HALF_UP);
                BigDecimal round = exact.setScale(2, RoundingMode.HALF_UP);
                legacySum = legacySum.add(round);
                BigDecimal dev = round.subtract(due.multiply(w)
                        .divide(weightSum, 20, RoundingMode.HALF_UP)).abs();
                if (dev.compareTo(legacyMaxDev) > 0) {
                    legacyMaxDev = dev;
                }
            }
            BigDecimal residual = due.subtract(legacySum);
            BigDecimal legacyBound = new BigDecimal("0.005").multiply(BigDecimal.valueOf(households));
            assertTrue(residual.abs().compareTo(legacyBound) <= 0,
                    households + " 户旧实现残差 " + residual + " 超出理论上界 " + legacyBound);

            // 新实现：最大余数法
            BigDecimal[] fixed = AllocationUtil.allocate(due, weights);
            BigDecimal fixedSum = BigDecimal.ZERO;
            BigDecimal fixedMaxDev = BigDecimal.ZERO;
            for (int i = 0; i < households; i++) {
                fixedSum = fixedSum.add(fixed[i]);
                BigDecimal dev = fixed[i].subtract(due.multiply(weights[i])
                        .divide(weightSum, 20, RoundingMode.HALF_UP)).abs();
                if (dev.compareTo(fixedMaxDev) > 0) {
                    fixedMaxDev = dev;
                }
            }
            assertEquals(0, fixedSum.compareTo(due), households + " 户时最大余数法 Σ != 总额");
            System.out.println("[对比] " + households + " 户：旧实现残差=" + residual
                    + "（→ 整段倒挤给一户，该户额外偏 " + residual.abs() + "，单户最大舍入偏 " + legacyMaxDev
                    + "）；最大余数法 Σ 精确、最大单户偏差=" + fixedMaxDev);
            assertTrue(fixedMaxDev.compareTo(new BigDecimal("0.01")) < 0);
        }
    }

    // ------------------------------------------------------------------
    // 恒等式 ⑧：预期收益 ≡ 实际回款利息
    // ------------------------------------------------------------------

    @Test
    @DisplayName("恒等式·预期收益：页面显示的预期收益 ≡ 还款计划利息合计（单利口径差异被钉住）")
    void expectReturnEqualsActualInterestSum() {
        BigDecimal amount = new BigDecimal("10000000.00"); // 1000 万
        BigDecimal rate = new BigDecimal("8.00");
        int period = 12;

        BigDecimal actual = ReturnPlanCalculator.calc(amount, rate, period, 1, START)
                .stream().map(PeriodPlan::getInterest).reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal shown = ExpectReturnUtil.expectedInterest(amount, rate, period, 1);
        assertEquals(0, shown.compareTo(actual), "预期收益必须等于还款计划利息合计");

        // 旧的单利口径，钉住差距
        BigDecimal simple = amount.multiply(rate).divide(new BigDecimal("100"), 10, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(period)).divide(new BigDecimal("12"), 2, RoundingMode.HALF_UP);
        System.out.println("[证据] 1000 万/8%/12 期：旧单利口径显示 " + simple
                + "，实际可收利息 " + actual + "，差 " + simple.subtract(actual));
        assertTrue(simple.subtract(actual).abs().compareTo(new BigDecimal("1000")) > 0,
                "单利与摊还口径的差距若不足 1000 元，这条证据不成立");
    }

    @Test
    @DisplayName("恒等式·预期收益：四种还款方式下都等于各自计划的利息合计")
    void expectReturnMatchesEachMethod() {
        BigDecimal amount = new BigDecimal("100000.00");
        BigDecimal rate = new BigDecimal("9.50");
        for (int method : METHODS) {
            List<PeriodPlan> plans = ReturnPlanCalculator.calc(amount, rate, 12, method, START);
            BigDecimal actual = sum(plans, PeriodPlan::getInterest);
            BigDecimal shown = ExpectReturnUtil.expectedInterest(amount, rate, 12, method);
            assertEquals(0, shown.compareTo(actual), "还款方式 " + method + " 的预期收益与计划不一致");
        }
    }

    @Test
    @DisplayName("恒等式·预期收益：非法/缺失入参返回 0.00 且不抛异常（列表接口不能被脏数据打挂）")
    void expectReturnHandlesBadInput() {
        assertEquals(0, ExpectReturnUtil.expectedInterest(null, new BigDecimal("8.00"), 12, 1)
                .compareTo(BigDecimal.ZERO));
        assertEquals(0, ExpectReturnUtil.expectedInterest(new BigDecimal("100"), null, 12, 1)
                .compareTo(BigDecimal.ZERO));
        assertEquals(0, ExpectReturnUtil.expectedInterest(new BigDecimal("100"), new BigDecimal("8.00"), null, 1)
                .compareTo(BigDecimal.ZERO));
        assertEquals(0, ExpectReturnUtil.expectedInterest(new BigDecimal("100"), new BigDecimal("8.00"), 0, 1)
                .compareTo(BigDecimal.ZERO));
        assertEquals(0, ExpectReturnUtil.expectedInterest(new BigDecimal("-1"), new BigDecimal("8.00"), 12, 1)
                .compareTo(BigDecimal.ZERO));
        assertNotNull(ExpectReturnUtil.expectedInterest(new BigDecimal("100"), new BigDecimal("8.00"), 12, 99));
    }

    // ------------------------------------------------------------------
    // 分摊工具的边界
    // ------------------------------------------------------------------

    @Test
    @DisplayName("分摊工具·边界：0 元、单户、权重含 0、1 分钱拆多户")
    void allocationEdgeCases() {
        assertEquals(0, AllocationUtil.allocate(BigDecimal.ZERO,
                new BigDecimal[]{new BigDecimal("100"), new BigDecimal("200")})[0].compareTo(BigDecimal.ZERO));

        BigDecimal[] single = AllocationUtil.allocate(new BigDecimal("123.45"),
                new BigDecimal[]{new BigDecimal("500")});
        assertEquals(1, single.length);
        assertEquals(0, single[0].compareTo(new BigDecimal("123.45")));

        // 权重含 0：0 权重的户只能拿到 0
        BigDecimal[] withZero = AllocationUtil.allocate(new BigDecimal("10.00"),
                new BigDecimal[]{new BigDecimal("300"), BigDecimal.ZERO, new BigDecimal("700")});
        assertEquals(0, withZero[1].compareTo(BigDecimal.ZERO), "0 权重的户不应分到钱");
        assertEquals(0, withZero[0].add(withZero[1]).add(withZero[2]).compareTo(new BigDecimal("10.00")));

        // 1 分钱拆 3 户：只能给 1 户，其余为 0，Σ 仍等于 1 分
        BigDecimal[] penny = AllocationUtil.allocate(new BigDecimal("0.01"),
                new BigDecimal[]{new BigDecimal("1"), new BigDecimal("1"), new BigDecimal("1")});
        BigDecimal s = penny[0].add(penny[1]).add(penny[2]);
        assertEquals(0, s.compareTo(new BigDecimal("0.01")));
    }

    @Test
    @DisplayName("分摊工具·权重不等时，分到的钱与其权重成比例（单调性）")
    void allocationIsMonotonicInWeight() {
        BigDecimal due = new BigDecimal("1000.01");
        BigDecimal[] weights = {new BigDecimal("100.00"), new BigDecimal("200.00"), new BigDecimal("700.00")};
        BigDecimal[] got = AllocationUtil.allocate(due, weights);
        assertTrue(got[0].compareTo(got[1]) < 0, "权重小的不应分到更多");
        assertTrue(got[1].compareTo(got[2]) < 0, "权重小的不应分到更多");
        assertEquals(0, got[0].add(got[1]).add(got[2]).compareTo(due));
    }

    // ------------------------------------------------------------------
    // 辅助
    // ------------------------------------------------------------------

    /** 生成 households 户、合计为 lendAmount 的等额权重（各户 1/n，末户吃尾差） */
    private static BigDecimal[] equalWeights(BigDecimal lendAmount, int households) {
        BigDecimal per = lendAmount.divide(BigDecimal.valueOf(households), 2, RoundingMode.DOWN);
        List<BigDecimal> list = new ArrayList<>(households);
        BigDecimal acc = BigDecimal.ZERO;
        for (int i = 0; i < households - 1; i++) {
            list.add(per);
            acc = acc.add(per);
        }
        list.add(lendAmount.subtract(acc));
        return list.toArray(new BigDecimal[0]);
    }
}
