package com.example.demo.entity.vo;

import com.example.demo.entity.Lend;
import lombok.Data;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 标的推荐结果 VO
 * <p>既用于「为你推荐」(/auth/recommend)，也用于「相似标的」(/{id}/similar)。
 * 推荐位永远不能白屏：兜底结果 score 会置 0 且 source 标记为 FALLBACK_HOT。
 */
@Data
public class RecommendLendVO {

    /** 标的本体，前端可直接复用列表卡片渲染 */
    private Lend lend;

    /** 综合得分 0~1，越大越靠前 */
    private BigDecimal score;

    /** 各维度得分明细（答辩/调参可展示，不参与前端逻辑） */
    private Map<String, BigDecimal> scoreDetail = new LinkedHashMap<>();

    /** 推荐理由，前端展示在卡片角标：如「高收益 12.5%」「与你偏好期限一致」 */
    private String reason;

    /** 标签，前端渲染成小 chip */
    private List<String> tags;

    /** 剩余可投金额 = amount - investAmount */
    private BigDecimal remainAmount;

    /** 募集进度 0~1（已募集 / 总额） */
    private BigDecimal progress;

    /** 结果来源：RULE / ITEM_CF / FUSION / FALLBACK_HOT —— 便于埋点与 AB 分析 */
    private String source;

    public static RecommendLendVO of(Lend lend, double score, String source) {
        RecommendLendVO vo = new RecommendLendVO();
        vo.setLend(lend);
        vo.setScore(BigDecimal.valueOf(score).setScale(4, java.math.RoundingMode.HALF_UP));
        vo.setSource(source);
        vo.setRemainAmount(remain(lend));
        vo.setProgress(progress(lend));
        return vo;
    }

    public static BigDecimal remain(Lend lend) {
        BigDecimal amount = lend.getAmount() == null ? BigDecimal.ZERO : lend.getAmount();
        BigDecimal invested = lend.getInvestAmount() == null ? BigDecimal.ZERO : lend.getInvestAmount();
        BigDecimal r = amount.subtract(invested);
        return r.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : r;
    }

    public static BigDecimal progress(Lend lend) {
        BigDecimal amount = lend.getAmount() == null ? BigDecimal.ZERO : lend.getAmount();
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal invested = lend.getInvestAmount() == null ? BigDecimal.ZERO : lend.getInvestAmount();
        BigDecimal p = invested.divide(amount, 4, java.math.RoundingMode.HALF_UP);
        return p.compareTo(BigDecimal.ONE) > 0 ? BigDecimal.ONE : p;
    }
}
