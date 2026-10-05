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
import java.util.List;

/**
 * P1 规则打分推荐 —— 多维度加权，无外部依赖、可解释、冷启动友好。
 *
 * <pre>
 * score = 0.35 × 收益分 + 0.25 × 期限匹配分 + 0.20 × 募集进度分 + 0.20 × 热度分
 * </pre>
 *
 * <ul>
 *   <li>收益分   = (rate - minRate) / (maxRate - minRate)        —— 利率越高越推荐</li>
 *   <li>期限匹配 = 1 - |period - 用户历史平均期数| / 36           —— 贴合既有偏好；新客取 0.5</li>
 *   <li>募集进度 = 1 - investAmount / amount                     —— 剩余越多越靠前，避免推"抢不到"的标</li>
 *   <li>热度     = investNum / 候选集最大 investNum               —— 冷启动兜底</li>
 * </ul>
 */
@Slf4j
@Component
public class RuleRecommendStrategy implements RecommendStrategy {

    private static final double W_RATE = 0.35;
    private static final double W_TERM = 0.25;
    private static final double W_PROGRESS = 0.20;
    private static final double W_HOT = 0.20;

    /** 期限归一化分母：与 publish 风控的期数上限 36 保持一致 */
    private static final double MAX_PERIOD = 36.0;
    /** 新客期限分兜底值 */
    private static final double COLD_START_TERM = 0.5;

    /**
     * 「复投」推荐理由：投过同一借款人。
     * <p>这是全站转化率最高的一类理由，融合阶段不允许被协同理由覆盖，故抽成常量共用。
     */
    public static final String REASON_REPEAT_BORROWER = "你投过该借款人的标的";

    @Override
    public String name() {
        return "RULE";
    }

    @Override
    public List<RecommendLendVO> recommend(RecommendContext ctx, int topN) {
        List<RecommendLendVO> result = new ArrayList<>();
        if (ctx.getCandidates() == null || ctx.getCandidates().isEmpty()) {
            return result;
        }

        double rateSpan = Math.max(1e-6, ctx.getMaxRate() - ctx.getMinRate());
        int maxInvestNum = Math.max(1, ctx.getMaxInvestNum());

        for (Lend lend : ctx.getCandidates()) {
            double rateScore = clamp01((rate(lend) - ctx.getMinRate()) / rateSpan);
            double termScore = ctx.isColdStart()
                    ? COLD_START_TERM
                    : clamp01(1.0 - Math.abs(period(lend) - ctx.getAvgPeriod()) / MAX_PERIOD);
            double progressScore = clamp01(1.0 - progress(lend));
            double hotScore = clamp01(investNum(lend) * 1.0 / maxInvestNum);

            double score = W_RATE * rateScore + W_TERM * termScore
                    + W_PROGRESS * progressScore + W_HOT * hotScore;

            RecommendLendVO vo = RecommendLendVO.of(lend, score, name());
            vo.getScoreDetail().put("收益分", scale(rateScore));
            vo.getScoreDetail().put("期限匹配分", scale(termScore));
            vo.getScoreDetail().put("募集进度分", scale(progressScore));
            vo.getScoreDetail().put("热度分", scale(hotScore));
            vo.setReason(ruleReason(ctx, lend, rateScore, termScore, progressScore, hotScore));
            vo.setTags(ruleTags(ctx, lend));
            result.add(vo);
        }

        result.sort(Comparator.comparing(RecommendLendVO::getScore).reversed());
        return result.size() > topN ? new ArrayList<>(result.subList(0, topN)) : result;
    }

    // ---------- 推荐理由（可解释性，显著提升点击率） ----------

    private String ruleReason(RecommendContext ctx, Lend lend,
                              double rateScore, double termScore,
                              double progressScore, double hotScore) {
        // 复投：投过同一借款人 —— 转化率最高的一类，优先展示
        if (lend.getUserId() != null && ctx.getInvestedBorrowerIds().contains(lend.getUserId())) {
            return REASON_REPEAT_BORROWER;
        }
        if (!ctx.isColdStart() && termScore >= 0.9) {
            return "与你偏好期限一致（" + period(lend) + "期）";
        }
        // 找出得分最高的维度作为理由
        double max = Math.max(Math.max(rateScore, termScore), Math.max(progressScore, hotScore));
        if (max == rateScore) {
            return "高收益 " + rateStr(lend) + "%";
        }
        if (max == progressScore) {
            return "新上架 · 剩余可投多";
        }
        if (max == hotScore) {
            return "热门标的 · " + investNum(lend) + " 人已投";
        }
        return "综合评分优选";
    }

    private List<String> ruleTags(RecommendContext ctx, Lend lend) {
        List<String> tags = new ArrayList<>();
        double rate = rate(lend);
        if (rate >= 11.0) {
            tags.add("高收益");
        } else if (rate <= 7.0) {
            tags.add("稳健低息");
        }
        if (lend.getUserId() != null && ctx.getInvestedBorrowerIds().contains(lend.getUserId())) {
            tags.add("复投");
        }
        if (progress(lend) < 0.2) {
            tags.add("新标的");
        } else if (progress(lend) >= 0.8) {
            tags.add("即将满标");
        }
        if (ctx.getAvailableBalance() != null && lend.getLowestAmount() != null
                && ctx.getAvailableBalance().compareTo(lend.getLowestAmount()) < 0) {
            tags.add("超出可投额度");
        }
        return tags;
    }

    // ---------- 取值与工具 ----------

    static double rate(Lend lend) {
        return lend.getLendYearRate() == null ? 0.0 : lend.getLendYearRate().doubleValue();
    }

    /** 利率展示串：去掉无意义的尾随 0（12.50 → 12.5） */
    static String rateStr(Lend lend) {
        return BigDecimal.valueOf(rate(lend)).setScale(2, RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString();
    }

    static int period(Lend lend) {
        return lend.getPeriod() == null ? 0 : lend.getPeriod();
    }

    static int investNum(Lend lend) {
        return lend.getInvestNum() == null ? 0 : lend.getInvestNum();
    }

    static double progress(Lend lend) {
        BigDecimal amount = lend.getAmount() == null ? BigDecimal.ZERO : lend.getAmount();
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return 0.0;
        }
        BigDecimal invested = lend.getInvestAmount() == null ? BigDecimal.ZERO : lend.getInvestAmount();
        return clamp01(invested.divide(amount, 6, RoundingMode.HALF_UP).doubleValue());
    }

    static double clamp01(double v) {
        if (Double.isNaN(v) || v < 0) {
            return 0.0;
        }
        return v > 1.0 ? 1.0 : v;
    }

    static BigDecimal scale(double v) {
        return BigDecimal.valueOf(v).setScale(4, RoundingMode.HALF_UP);
    }
}
