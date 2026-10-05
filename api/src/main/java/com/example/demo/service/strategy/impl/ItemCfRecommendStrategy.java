package com.example.demo.service.strategy.impl;

import com.example.demo.entity.Lend;
import com.example.demo.entity.vo.RecommendLendVO;
import com.example.demo.service.strategy.RecommendContext;
import com.example.demo.service.strategy.RecommendStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * P2 Item-CF 协同过滤（进阶亮点）。
 *
 * <pre>
 * 把 lend_item 的 (投资人, 标的) 当隐式评分矩阵（投过=1）
 * Step1 物品向量 U(i) = 投过标的 i 的投资人集合
 * Step2 余弦相似度 sim(i,j) = |U(i) ∩ U(j)| / sqrt(|U(i)| × |U(j)|)
 * Step3 用户 u 对未投标的 j 的打分 score(u,j) = Σ_{i ∈ 已投(u)} sim(i,j)
 * Step4 TopN，与规则分融合（规则 0.4 + 协同 0.6）
 * </pre>
 *
 * <p><b>冷启动兜底</b>：本项目 lend_item 数据量小（<10 条），CF 信号常常全为 0。
 * 因此 /similar 采用「协同 0.6 + 内容 0.4」的混合相似度，保证推荐位不白屏；
 * 上层融合时若检测到 CF 无信号（maxScore==0），会退化为纯规则分。
 */
@Slf4j
@Component
public class ItemCfRecommendStrategy implements RecommendStrategy {

    /** 融合权重：协同 */
    private static final double W_CF = 0.6;
    /** 融合权重：内容 */
    private static final double W_CONTENT = 0.4;

    private static final double MAX_PERIOD = 36.0;
    private static final double RATE_SPAN = 9.6; // 14.6 - 5.0
    /** 被推荐标的的投资人集合过小时，CF 噪声大于信号，直接降权 */
    private static final int MIN_VECTOR_SIZE = 2;

    @Override
    public String name() {
        return "ITEM_CF";
    }

    // ================= 用户级 CF（/auth/recommend 融合用） =================

    @Override
    public List<RecommendLendVO> recommend(RecommendContext ctx, int topN) {
        List<RecommendLendVO> result = new ArrayList<>();
        if (ctx.getCandidates() == null || ctx.getCandidates().isEmpty()
                || ctx.getInvestedLendIds() == null || ctx.getInvestedLendIds().isEmpty()) {
            return result; // 无行为 → 无 CF 信号，交给上层退化到规则分
        }

        Map<Long, Double> raw = userCfScores(ctx.getInvestedLendIds(), ctx.getLendInvestors(),
                ctx.getCandidates());
        if (raw.isEmpty()) {
            return result;
        }
        double max = raw.values().stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        if (max <= 0.0) {
            return result; // 全是 0 → 视为无信号
        }

        for (Lend lend : ctx.getCandidates()) {
            Double s = raw.get(lend.getId());
            if (s == null || s <= 0.0) {
                continue;
            }
            double norm = s / max;
            RecommendLendVO vo = RecommendLendVO.of(lend, norm, name());
            vo.getScoreDetail().put("协同分", scale(norm));
            vo.setReason("投过相似标的的投资人也在投");
            vo.setTags(new ArrayList<>());
            result.add(vo);
        }
        result.sort(Comparator.comparing(RecommendLendVO::getScore).reversed());
        return result.size() > topN ? new ArrayList<>(result.subList(0, topN)) : result;
    }

    /**
     * score(u, j) = Σ_{i ∈ 已投(u)} sim(i, j)
     */
    private Map<Long, Double> userCfScores(Set<Long> investedLendIds,
                                           Map<Long, Set<Long>> lendInvestors,
                                           List<Lend> candidates) {
        Map<Long, Double> scores = new HashMap<>();
        for (Lend cand : candidates) {
            Set<Long> target = lendInvestors.get(cand.getId());
            double sum = 0.0;
            for (Long invested : investedLendIds) {
                Set<Long> source = lendInvestors.get(invested);
                sum += cosine(source, target);
            }
            if (sum > 0) {
                scores.put(cand.getId(), sum);
            }
        }
        return scores;
    }

    // ================= 物品级相似（/{id}/similar 用） =================

    /**
     * 混合相似度：协同(0.6) + 内容(0.4)。
     * <p>协同为 0 时（数据稀疏），完全退化为内容相似，保证结果非空。
     */
    public List<RecommendLendVO> similar(Lend target, List<Lend> candidates,
                                         Map<Long, Set<Long>> lendInvestors, int topN) {
        List<RecommendLendVO> result = new ArrayList<>();
        if (target == null || candidates == null || candidates.isEmpty()) {
            return result;
        }
        Set<Long> targetUsers = lendInvestors.getOrDefault(target.getId(), new HashSet<>());

        for (Lend cand : candidates) {
            if (cand.getId() == null || cand.getId().equals(target.getId())) {
                continue;
            }
            Set<Long> candUsers = lendInvestors.getOrDefault(cand.getId(), new HashSet<>());
            double cf = cosine(targetUsers, candUsers);
            double content = contentSimilar(target, cand);
            double score = W_CF * cf + W_CONTENT * content;

            RecommendLendVO vo = RecommendLendVO.of(cand, score, name());
            vo.getScoreDetail().put("协同相似度", scale(cf));
            vo.getScoreDetail().put("内容相似度", scale(content));
            vo.setReason(similarReason(target, cand, cf, content));
            vo.setTags(similarTags(target, cand));
            result.add(vo);
        }
        result.sort(Comparator.comparing(RecommendLendVO::getScore).reversed());
        return result.size() > topN ? new ArrayList<>(result.subList(0, topN)) : result;
    }

    private String similarReason(Lend target, Lend cand, double cf, double content) {
        if (cf > 0) {
            return "投过该标的的投资人也在投";
        }
        if (samePeriod(target, cand) && closeRate(target, cand)) {
            return "同期限 · 同利率区间";
        }
        if (samePeriod(target, cand)) {
            return "期限相同（" + safePeriod(cand) + "期）";
        }
        if (closeRate(target, cand)) {
            return "收益率接近（" + rateStr(cand) + "%）";
        }
        return "综合相似";
    }

    private List<String> similarTags(Lend target, Lend cand) {
        List<String> tags = new ArrayList<>();
        if (samePeriod(target, cand)) {
            tags.add("同期数");
        }
        if (closeRate(target, cand)) {
            tags.add("同利率段");
        }
        if (sameReturnMethod(target, cand)) {
            tags.add("同还款方式");
        }
        return tags;
    }

    /**
     * 内容相似度：期限 0.40 + 利率 0.35 + 起投门槛 0.15 + 还款方式 0.10。
     * 用于协同信号缺失时的兜底，也是"同类标的"的合理定义。
     */
    private double contentSimilar(Lend a, Lend b) {
        double p = 1.0 - Math.min(1.0, Math.abs(safePeriod(a) - safePeriod(b)) / MAX_PERIOD);
        double r = 1.0 - Math.min(1.0, Math.abs(rawRate(a) - rawRate(b)) / RATE_SPAN);
        double m = lowestAmountSim(a, b);
        double method = sameReturnMethod(a, b) ? 1.0 : 0.4;
        return clamp01(0.40 * p + 0.35 * r + 0.15 * m + 0.10 * method);
    }

    private double lowestAmountSim(Lend a, Lend b) {
        double la = lowestAmount(a);
        double lb = lowestAmount(b);
        double max = Math.max(la, lb);
        if (max <= 0) {
            return 1.0;
        }
        return clamp01(1.0 - Math.abs(la - lb) / max);
    }

    // ================= 工具 =================

    /** 余弦相似度：|A ∩ B| / sqrt(|A| × |B|) */
    static double cosine(Set<Long> a, Set<Long> b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) {
            return 0.0;
        }
        if (a.size() < MIN_VECTOR_SIZE && b.size() < MIN_VECTOR_SIZE) {
            return 0.0; // 单元素向量不构成有效共现
        }
        int inter = 0;
        Set<Long> small = a.size() <= b.size() ? a : b;
        Set<Long> large = small == a ? b : a;
        for (Long x : small) {
            if (large.contains(x)) {
                inter++;
            }
        }
        if (inter == 0) {
            return 0.0;
        }
        return inter / Math.sqrt((double) a.size() * b.size());
    }

    private static boolean samePeriod(Lend a, Lend b) {
        return safePeriod(a) == safePeriod(b);
    }

    private static boolean sameReturnMethod(Lend a, Lend b) {
        Integer x = a.getReturnMethod();
        Integer y = b.getReturnMethod();
        return x != null && x.equals(y);
    }

    private static boolean closeRate(Lend a, Lend b) {
        return Math.abs(rawRate(a) - rawRate(b)) <= 1.0;
    }

    private static int safePeriod(Lend l) {
        return l.getPeriod() == null ? 0 : l.getPeriod();
    }

    private static double rawRate(Lend l) {
        return l.getLendYearRate() == null ? 0.0 : l.getLendYearRate().doubleValue();
    }

    private static String rateStr(Lend l) {
        return BigDecimal.valueOf(rawRate(l)).setScale(2, RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString();
    }

    private static double lowestAmount(Lend l) {
        return l.getLowestAmount() == null ? 0.0 : l.getLowestAmount().doubleValue();
    }

    private static double clamp01(double v) {
        if (Double.isNaN(v) || v < 0) {
            return 0.0;
        }
        return v > 1.0 ? 1.0 : v;
    }

    /** 与 {@link RuleRecommendStrategy} 保持同一精度（4 位小数） */
    private static BigDecimal scale(double v) {
        return BigDecimal.valueOf(v).setScale(4, RoundingMode.HALF_UP);
    }
}
