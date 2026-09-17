package com.example.demo.utils;

import com.example.demo.enums.ReturnMethodEnum;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 还款计划计算工具类
 * 支持四种还款方式：
 * 1 等额本息：月供 = A × i × (1+i)^n / ((1+i)^n - 1)
 * 2 等额本金：每月本金 = A/n，每月利息 = 剩余本金 × i
 * 3 每月还息到期还本：前 n-1 期只还利息，末期还本+息
 * 4 一次还本付息：到期一次还本金+全部利息
 *
 * 利率单位约定：yearRate 为百分数（如 8.00 表示 8%），内部统一转月利率 i = yearRate/100/12
 */
public class ReturnPlanCalculator {

    /** 内部计算精度（位） */
    private static final int INNER_SCALE = 10;
    /** 输出金额精度（2位小数） */
    private static final int MONEY_SCALE = 2;
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal TWELVE = new BigDecimal("12");

    /**
     * 每期还款计划
     */
    public static class PeriodPlan {
        /** 期数（从1开始） */
        private int period;
        /** 本期本金 */
        private BigDecimal principal;
        /** 本期利息 */
        private BigDecimal interest;
        /** 本期应还总额 */
        private BigDecimal total;
        /** 还款后剩余本金 */
        private BigDecimal baseAmount;
        /** 应还日期 */
        private LocalDate returnDate;

        public PeriodPlan(int period, BigDecimal principal, BigDecimal interest,
                          BigDecimal total, BigDecimal baseAmount, LocalDate returnDate) {
            this.period = period;
            this.principal = principal;
            this.interest = interest;
            this.total = total;
            this.baseAmount = baseAmount;
            this.returnDate = returnDate;
        }

        public int getPeriod() { return period; }
        public BigDecimal getPrincipal() { return principal; }
        public BigDecimal getInterest() { return interest; }
        public BigDecimal getTotal() { return total; }
        public BigDecimal getBaseAmount() { return baseAmount; }
        public LocalDate getReturnDate() { return returnDate; }
    }

    /**
     * 生成还款计划
     * @param amount       贷款本金 A
     * @param yearRate     年化利率（百分数，如 8.00 表示 8%）
     * @param months       还款月数 n
     * @param returnMethod 还款方式（见 ReturnMethodEnum）
     * @param startDate    放款日（首期还款日为下月同日）
     */
    public static List<PeriodPlan> calc(BigDecimal amount, BigDecimal yearRate,
                                        int months, int returnMethod, LocalDate startDate) {
        ReturnMethodEnum method = ReturnMethodEnum.of(returnMethod);
        if (method == null) {
            throw new IllegalArgumentException("不支持的还款方式：" + returnMethod);
        }
        switch (method) {
            case EQUAL_PRINCIPAL_INTEREST:
                return equalPrincipalAndInterest(amount, yearRate, months, startDate);
            case EQUAL_PRINCIPAL:
                return equalPrincipal(amount, yearRate, months, startDate);
            case MONTHLY_INTEREST_LUMP_PRINCIPAL:
                return monthlyInterestLumpPrincipal(amount, yearRate, months, startDate);
            case LUMP_SUM:
                return lumpSum(amount, yearRate, months, startDate);
            default:
                throw new IllegalArgumentException("不支持的还款方式：" + returnMethod);
        }
    }

    /**
     * 1. 等额本息
     */
    private static List<PeriodPlan> equalPrincipalAndInterest(BigDecimal amount, BigDecimal yearRate,
                                                              int months, LocalDate startDate) {
        BigDecimal i = monthRate(yearRate);
        // 月供 X = A × i × (1+i)^n / ((1+i)^n - 1)
        BigDecimal pow = i.add(BigDecimal.ONE).pow(months);
        BigDecimal monthlyPayment = amount
                .multiply(i)
                .multiply(pow)
                .divide(pow.subtract(BigDecimal.ONE), INNER_SCALE, RoundingMode.HALF_UP);

        List<PeriodPlan> list = new ArrayList<>(months);
        BigDecimal remaining = amount;
        for (int k = 1; k <= months; k++) {
            // 当月利息 = 月初剩余本金 × 月利率
            BigDecimal interest = remaining.multiply(i);
            BigDecimal principal;
            if (k < months) {
                principal = monthlyPayment.subtract(interest);
            } else {
                // 最后一期：剩余本金全部还清，修正尾差
                principal = remaining;
            }
            BigDecimal total = principal.add(interest);
            remaining = remaining.subtract(principal);

            list.add(buildPlan(k, principal, interest, total, remaining, startDate));
        }
        return list;
    }

    /**
     * 2. 等额本金
     */
    private static List<PeriodPlan> equalPrincipal(BigDecimal amount, BigDecimal yearRate,
                                                   int months, LocalDate startDate) {
        BigDecimal i = monthRate(yearRate);
        // 每月固定本金 = A / n（内部精度，输出2位）
        BigDecimal monthPrincipal = amount.divide(new BigDecimal(months), INNER_SCALE, RoundingMode.HALF_UP);

        List<PeriodPlan> list = new ArrayList<>(months);
        BigDecimal remaining = amount;
        for (int k = 1; k <= months; k++) {
            BigDecimal interest = remaining.multiply(i);
            BigDecimal principal;
            if (k < months) {
                principal = monthPrincipal;
            } else {
                // 最后一期偿还剩余全部本金
                principal = remaining;
            }
            BigDecimal total = principal.add(interest);
            remaining = remaining.subtract(principal);

            list.add(buildPlan(k, principal, interest, total, remaining, startDate));
        }
        return list;
    }

    /**
     * 3. 每月还息到期还本
     */
    private static List<PeriodPlan> monthlyInterestLumpPrincipal(BigDecimal amount, BigDecimal yearRate,
                                                                 int months, LocalDate startDate) {
        BigDecimal i = monthRate(yearRate);
        BigDecimal monthlyInterest = amount.multiply(i);

        List<PeriodPlan> list = new ArrayList<>(months);
        for (int k = 1; k <= months; k++) {
            BigDecimal principal;
            BigDecimal interest;
            if (k < months) {
                principal = BigDecimal.ZERO;
                interest = monthlyInterest;
            } else {
                principal = amount;
                interest = monthlyInterest;
            }
            BigDecimal total = principal.add(interest);
            BigDecimal remaining = amount.subtract(principal);

            list.add(buildPlan(k, principal, interest, total, remaining, startDate));
        }
        return list;
    }

    /**
     * 4. 一次还本付息（只生成1期，到期日 = 放款日 + n 个月）
     */
    private static List<PeriodPlan> lumpSum(BigDecimal amount, BigDecimal yearRate,
                                            int months, LocalDate startDate) {
        BigDecimal i = monthRate(yearRate);
        // 总利息 = A × 月利率 × 月数
        BigDecimal interest = amount.multiply(i).multiply(new BigDecimal(months));
        BigDecimal principal = amount;
        BigDecimal total = principal.add(interest);
        LocalDate returnDate = startDate.plusMonths(months);

        PeriodPlan plan = new PeriodPlan(
                1,
                money(principal),
                money(interest),
                money(total),
                BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP),
                returnDate);
        List<PeriodPlan> list = new ArrayList<>(1);
        list.add(plan);
        return list;
    }

    /**
     * 构建一期计划：金额保留2位、还款日 = 放款日 + k 个月
     */
    private static PeriodPlan buildPlan(int k, BigDecimal principal, BigDecimal interest,
                                        BigDecimal total, BigDecimal remaining, LocalDate startDate) {
        return new PeriodPlan(
                k,
                money(principal),
                money(interest),
                money(total),
                money(remaining),
                startDate.plusMonths(k));
    }

    /** 年化百分数转月利率：8.00 → 0.006666... */
    private static BigDecimal monthRate(BigDecimal yearRate) {
        return yearRate.divide(HUNDRED, INNER_SCALE, RoundingMode.HALF_UP)
                .divide(TWELVE, INNER_SCALE, RoundingMode.HALF_UP);
    }

    /** 金额保留2位小数 */
    private static BigDecimal money(BigDecimal v) {
        return v.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
