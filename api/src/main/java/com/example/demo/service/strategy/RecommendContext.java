package com.example.demo.service.strategy;

import com.example.demo.entity.Lend;
import lombok.Data;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 推荐上下文：把一次推荐所需的全部输入打包，避免策略类各自去查库。
 * <p>由 {@code RecommendServiceImpl} 统一装配，策略实现只做纯计算（可单测）。
 */
@Data
public class RecommendContext {

    /** 当前用户 ID（未登录时为 null，走热门兜底） */
    private Long userId;

    /** 候选标的（已通过硬过滤：募资中 / 未满标 / 非本人 / 未投过） */
    private List<Lend> candidates;

    /** 当前用户已投标的标的 ID */
    private Set<Long> investedLendIds = new HashSet<>();

    /** 当前用户投过的借款人 ID 集合（lend.user_id），用于「复投」理由 */
    private Set<Long> investedBorrowerIds = new HashSet<>();

    /** 当前用户历史投资的平均期数，null 表示新客（冷启动） */
    private Double avgPeriod;

    /** 标的 → 投资人集合，Item-CF 的物品向量 */
    private Map<Long, Set<Long>> lendInvestors = new HashMap<>();

    /** 当前用户可用余额，用于「可投能力」过滤与标签 */
    private java.math.BigDecimal availableBalance;

    /** 候选集中的最大投资人数，用于热度归一化 */
    private int maxInvestNum = 1;

    /** 候选集最小/最大年化利率，用于收益归一化 */
    private double minRate = 5.0;
    private double maxRate = 14.6;

    public boolean isColdStart() {
        return avgPeriod == null || investedLendIds.isEmpty();
    }
}
