package com.example.demo.service.strategy;

import com.example.demo.entity.vo.RecommendLendVO;

import java.util.List;

/**
 * 推荐策略（策略模式）。
 * <p>项目后续要叠加 User-CF / 画像 / 实时特征时，只需新增一个实现类并在
 * {@code RecommendServiceImpl} 里编排融合，不动 Controller 与前端。
 */
public interface RecommendStrategy {

    /** 策略名，用于日志与 {@link RecommendLendVO#getSource()} */
    String name();

    /**
     * 执行推荐。
     *
     * @param ctx  推荐上下文（候选集 + 用户画像）
     * @param topN 期望返回条数
     * @return 已按得分降序排列的结果；候选为空或无信号时返回空列表（由上层兜底）
     */
    List<RecommendLendVO> recommend(RecommendContext ctx, int topN);
}
