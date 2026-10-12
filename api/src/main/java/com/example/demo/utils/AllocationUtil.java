package com.example.demo.utils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Comparator;

/**
 * 最大余数法（Hamilton / Largest Remainder）精确分摊。
 *
 * <p><b>为什么要它：</b>原来投资人回款明细是「每户按占比各自四舍五入到分」，
 * 再用 {@code balanceItemReturns} 把<b>整段残差一次性倒挤给某一户</b>。
 * 于是：
 * <ul>
 *   <li>残差上界 ≈ {@code 0.005 × 户数}（1000 户最坏 ±5.00 元 / 期；
 *       1000 万拆成 10 万户时最坏 ±500 元 / 期）；</li>
 *   <li>而且残差<b>每期都压在同一户</b>身上，跨期累积；</li>
 *   <li>倒挤量可正可负，负数落到小额户上会直接把该户回款压成负数。</li>
 * </ul>
 *
 * <p>本类改用金融行业标准做法，把差额<b>均匀分散</b>：
 * <ol>
 *   <li>以「分」为最小单位做整数运算（{@code long}），全流程零浮点；</li>
 *   <li>每户先取整数分（向下取整）；</li>
 *   <li>剩余的分按「小数部分从大到小」逐户 +1 分；</li>
 *   <li>排序并列时按权重降序、再按下标升序 ⇒ <b>结果完全可复现</b>。</li>
 * </ol>
 *
 * <p><b>可证明的性质：</b>
 * <ul>
 *   <li>Σ分摊结果 ≡ 总额（分位严格相等，不是"近似"）；</li>
 *   <li>单户偏差严格小于 1 分（{@code |分配 - 精确份额| < 0.01}）；</li>
 *   <li>结果与入参顺序无关（并列时由下标决定，同一输入必得同一输出）。</li>
 * </ul>
 */
public final class AllocationUtil {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    /** 计算精确份额时的内部精度 */
    private static final int INNER_SCALE = 12;

    private AllocationUtil() {
    }

    /**
     * 把 {@code total} 按 {@code weights} 的比例精确切分。
     *
     * @param total   待分摊总额（元，最多 2 位小数；必须 ≥ 0）
     * @param weights 各户权重（通常为投资金额），必须非负且不全为 0
     * @return 与 {@code weights} 等长的分摊结果，每项 2 位小数，Σ ≡ total
     */
    public static BigDecimal[] allocate(BigDecimal total, BigDecimal[] weights) {
        if (weights == null) {
            throw new IllegalArgumentException("weights 不能为 null");
        }
        if (weights.length == 0) {
            return new BigDecimal[0];
        }
        BigDecimal t = (total == null ? BigDecimal.ZERO : total).setScale(2, RoundingMode.HALF_UP);
        if (t.signum() < 0) {
            throw new IllegalArgumentException("待分摊总额不能为负：" + t);
        }

        long[] w = new long[weights.length];
        long wSum = 0L;
        for (int i = 0; i < weights.length; i++) {
            BigDecimal wi = weights[i] == null ? BigDecimal.ZERO : weights[i];
            if (wi.signum() < 0) {
                throw new IllegalArgumentException("权重不能为负：index=" + i + ", weight=" + wi);
            }
            // 权重统一按"分"取整，避免按元比较时因小数位不同而失真
            w[i] = wi.setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact();
            wSum += w[i];
        }
        if (wSum <= 0L) {
            throw new IllegalArgumentException("权重合计必须大于 0");
        }

        long totalCents = t.movePointRight(2).longValueExact();
        BigDecimal cents = BigDecimal.valueOf(totalCents);

        long[] allocated = new long[weights.length];
        BigDecimal[] remainder = new BigDecimal[weights.length];
        long allocatedSum = 0L;
        for (int i = 0; i < weights.length; i++) {
            BigDecimal exact = cents
                    .multiply(BigDecimal.valueOf(w[i]))
                    .divide(BigDecimal.valueOf(wSum), INNER_SCALE, RoundingMode.DOWN);
            long floor = exact.setScale(0, RoundingMode.FLOOR).longValueExact();
            allocated[i] = floor;
            remainder[i] = exact.subtract(BigDecimal.valueOf(floor));
            allocatedSum += floor;
        }

        // 剩余的分：按「小数部分降序 → 权重降序 → 下标升序」发放
        long left = totalCents - allocatedSum;
        if (left > 0) {
            Integer[] order = new Integer[weights.length];
            for (int i = 0; i < order.length; i++) {
                order[i] = i;
            }
            Arrays.sort(order, Comparator
                    .comparing((Integer i) -> remainder[i], Comparator.reverseOrder())
                    .thenComparing((Integer i) -> w[i], Comparator.reverseOrder())
                    .thenComparing(Comparator.naturalOrder()));
            for (int k = 0; k < left && k < order.length; k++) {
                allocated[order[k]] = allocated[order[k]] + 1L;
            }
        }

        BigDecimal[] result = new BigDecimal[weights.length];
        for (int i = 0; i < weights.length; i++) {
            result[i] = BigDecimal.valueOf(allocated[i]).divide(HUNDRED).setScale(2, RoundingMode.UNNECESSARY);
        }
        return result;
    }
}
