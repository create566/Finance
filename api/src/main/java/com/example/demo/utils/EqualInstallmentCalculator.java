package com.example.demo.utils;

import lombok.Data;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 等额本息计算工具类
 */
public class EqualInstallmentCalculator {

    /**
     * 计算等额本息每月还款金额
     * @param principal 贷款本金
     * @param annualRate 年利率(如4.9%传4.9)
     * @param months 贷款期限(月数)
     * @return 每月还款额(四舍五入保留2位小数)
     */
    public static BigDecimal calculateMonthlyPayment(double principal, double annualRate, int months) {
        // 月利率 = 年利率/12
        BigDecimal monthlyRate = BigDecimal.valueOf(annualRate)
                .divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP)
                .divide(BigDecimal.valueOf(12), 10, RoundingMode.HALF_UP);

        // (1+月利率)^还款月数
        BigDecimal temp = monthlyRate.add(BigDecimal.ONE).pow(months);

        // 每月还款额 = 本金×月利率×(1+月利率)^还款月数 ÷ [(1+月利率)^还款月数-1]
        BigDecimal monthlyPayment = BigDecimal.valueOf(principal)
                .multiply(monthlyRate)
                .multiply(temp)
                .divide(temp.subtract(BigDecimal.ONE), 2, RoundingMode.HALF_UP);

        return monthlyPayment;
    }

    /**
     * 生成等额本息还款计划表
     * @param principal 贷款本金
     * @param annualRate 年利率
     * @param months 贷款期限(月数)
     * @return 还款计划列表
     */
    public static List<PaymentDetail> generatePaymentSchedule(double principal, double annualRate, int months) {
        List<PaymentDetail> schedule = new ArrayList<>();
        BigDecimal monthlyPayment = calculateMonthlyPayment(principal, annualRate, months);  //月供
        BigDecimal remainingPrincipal = BigDecimal.valueOf(principal);
        BigDecimal totalInterest = BigDecimal.ZERO;

        // 月利率
        BigDecimal monthlyRate = BigDecimal.valueOf(annualRate)
                .divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP)
                .divide(BigDecimal.valueOf(12), 10, RoundingMode.HALF_UP);

        for (int i = 1; i <= months; i++) {
            // 当月利息 = 剩余本金 × 月利率
            BigDecimal monthlyInterest = remainingPrincipal.multiply(monthlyRate)
                    .setScale(2, RoundingMode.HALF_UP);

            // 当月本金 = 月供 - 当月利息
            BigDecimal monthlyPrincipal = monthlyPayment.subtract(monthlyInterest);

            // 调整最后一期的本金，避免因四舍五入导致的误差
            if (i == months) {
                monthlyPrincipal = remainingPrincipal;
                monthlyPayment = monthlyPrincipal.add(monthlyInterest);
            }

            // 剩余本金 = 剩余本金 - 当月本金
            remainingPrincipal = remainingPrincipal.subtract(monthlyPrincipal);

            // 累计总利息
            totalInterest = totalInterest.add(monthlyInterest);

            // 添加到还款计划
            schedule.add(new PaymentDetail(
                    i,
                    monthlyPayment,
                    monthlyPrincipal,
                    monthlyInterest,
                    remainingPrincipal.compareTo(BigDecimal.ZERO) > 0 ? remainingPrincipal : BigDecimal.ZERO,
                    totalInterest
            ));
        }

        return schedule;
    }

    /**
     * 还款明细类
     */
    @Data
    public static class PaymentDetail {
        private int period;          // 期数
        private BigDecimal payment; // 月供
        private BigDecimal principal;// 本金
        private BigDecimal interest;// 利息
        private BigDecimal remainingPrincipal; // 剩余本金
        private BigDecimal totalInterest;      // 累计利息

        public PaymentDetail(int period, BigDecimal payment, BigDecimal principal,
                             BigDecimal interest, BigDecimal remainingPrincipal,
                             BigDecimal totalInterest) {
            this.period = period;
            this.payment = payment;
            this.principal = principal;
            this.interest = interest;
            this.remainingPrincipal = remainingPrincipal;
            this.totalInterest = totalInterest;
        }


        @Override
        public String toString() {
            NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(Locale.CHINA);
            return String.format("第%02d期 | 月供: %-10s | 本金: %-10s | 利息: %-10s | 剩余本金: %-12s | 累计利息: %s",
                    period,
                    currencyFormat.format(payment),
                    currencyFormat.format(principal),
                    currencyFormat.format(interest),
                    currencyFormat.format(remainingPrincipal),
                    currencyFormat.format(totalInterest));
        }
    }

    public static void main(String[] args) {
        double principal = 1000000;  // 贷款本金100万
        double annualRate = 4.9;     // 年利率4.9%
        int years = 20;              // 贷款期限20年
        int months = years * 12;     // 转换为月数

        // 计算月供
        BigDecimal monthlyPayment = calculateMonthlyPayment(principal, annualRate, months);
        System.out.println("贷款金额: " + NumberFormat.getCurrencyInstance(Locale.CHINA).format(principal));
        System.out.println("贷款期限: " + years + "年(" + months + "个月)");
        System.out.println("年利率: " + annualRate + "%");
        System.out.println("每月还款额: " + NumberFormat.getCurrencyInstance(Locale.CHINA).format(monthlyPayment));
        System.out.println("----------------------------------------");

        // 生成还款计划表(打印前5期和最后5期)
        List<PaymentDetail> schedule = generatePaymentSchedule(principal, annualRate, months);
        System.out.println("等额本息还款计划表:");
        for (int i = 0; i < 5; i++) {
            System.out.println(schedule.get(i));
        }
        System.out.println("...");
        for (int i = months - 5; i < months; i++) {
            System.out.println(schedule.get(i));
        }

        // 计算总利息
        BigDecimal totalInterest = schedule.get(schedule.size() - 1).getTotalInterest();
        System.out.println("----------------------------------------");
        System.out.println("总利息: " + NumberFormat.getCurrencyInstance(Locale.CHINA).format(totalInterest));
        System.out.println("总还款额: " + NumberFormat.getCurrencyInstance(Locale.CHINA)
                .format(BigDecimal.valueOf(principal).add(totalInterest)));
    }
}