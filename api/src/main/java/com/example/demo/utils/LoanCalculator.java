package com.example.demo.utils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

public class LoanCalculator {

    /**
     * 计算等额本息每月还款利息
     * @param invest 贷款本金
     * @param yearRate 年利率(如4.9%传0.049)
     * @param totalMonth 贷款总月数
     * @return 按月排序的利息Map(月份→当月利息)
     */
    public static Map<Integer, BigDecimal> getPerMonthInterest(BigDecimal invest, BigDecimal yearRate, int totalMonth) {
        Map<Integer, BigDecimal> interestMap = new LinkedHashMap<>();

        // 计算月利率
        BigDecimal monthRate = yearRate.divide(BigDecimal.valueOf(12), 10, RoundingMode.HALF_UP);

        // 计算每月还款额 [本金×月利率×(1+月利率)^还款月数]÷[(1+月利率)^还款月数-1]
        BigDecimal monthlyPayment = invest.multiply(monthRate)
                .multiply(BigDecimal.ONE.add(monthRate).pow(totalMonth))
                .divide(BigDecimal.ONE.add(monthRate).pow(totalMonth).subtract(BigDecimal.ONE),
                        2, RoundingMode.HALF_UP);
        BigDecimal remainingPrincipal = invest; // 剩余本金
        for (int month = 1; month <= totalMonth; month++) {
            // 当月利息 = 剩余本金 × 月利率
            BigDecimal monthlyInterest = remainingPrincipal.multiply(monthRate)
                    .setScale(2, RoundingMode.HALF_UP);
            // 当月本金 = 月供 - 当月利息
            BigDecimal monthlyPrincipal = monthlyPayment.subtract(monthlyInterest);
            // 最后一期调整，避免因四舍五入导致的误差
            if (month == totalMonth) {
                monthlyPrincipal = remainingPrincipal;
                monthlyInterest = monthlyPayment.subtract(monthlyPrincipal);
            }
            // 记录当月利息
            interestMap.put(month, monthlyInterest);
            // 更新剩余本金
            remainingPrincipal = remainingPrincipal.subtract(monthlyPrincipal);
        }
        return interestMap;
    }

    public static void main(String[] args) {
        // 示例：100万贷款，年利率4.9%，20年(240个月)
        BigDecimal principal = new BigDecimal("1000000");
        BigDecimal annualRate = new BigDecimal("0.049");
        int months = 240;

        Map<Integer, BigDecimal> interestSchedule = getPerMonthInterest(principal, annualRate, months);
        // 打印前12个月和最后12个月的利息
        System.out.println("等额本息每月利息明细：");
        System.out.println("前12个月：");
        for (int i = 1; i <= 12; i++) {
            System.out.printf("第%3d月利息: %,.2f元%n", i, interestSchedule.get(i));
        }
        System.out.println("\n最后12个月：");
        for (int i = months - 11; i <= months; i++) {
            System.out.printf("第%3d月利息: %,.2f元%n", i, interestSchedule.get(i));
        }
        // 计算总利息
        BigDecimal totalInterest = interestSchedule.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        System.out.printf("%n总利息: %,.2f元%n", totalInterest);
    }
}
