package com.example.demo.service;

import com.example.demo.entity.vo.RecommendLendVO;

import java.util.List;

/**
 * 标的推荐服务。
 * <p>对外两个能力：
 * <ul>
 *   <li>{@link #recommendForUser} —— 个性化「为你推荐」（规则 0.4 + Item-CF 0.6 融合）</li>
 *   <li>{@link #similarTo}      —— 详情页「相似标的」（物品相似度混合）</li>
 * </ul>
 * <b>铁律：推荐位永远不能白屏。</b>任何异常/无信号都会退化为热门榜。
 */
public interface RecommendService {

    /**
     * 为当前登录用户推荐标的。
     *
     * @param userId 用户 ID，可为 null（未登录 → 热门兜底）
     * @param topN   返回条数，默认 5
     */
    List<RecommendLendVO> recommendForUser(Long userId, Integer topN);

    /**
     * 与指定标的最相似的标的（详情页用）。
     *
     * @param lendId 目标标的 ID
     * @param topN   返回条数，默认 5
     */
    List<RecommendLendVO> similarTo(Long lendId, Integer topN);

    /**
     * 使所有用户的推荐缓存失效（标的发布/满标/流标/下架时调用）。
     * <p>实现走 version 自增，不做 KEYS 扫描。
     */
    void evictAll();

    /**
     * 仅使某个用户的推荐缓存失效（该用户完成投资后调用）。
     */
    void evict(Long userId);
}
