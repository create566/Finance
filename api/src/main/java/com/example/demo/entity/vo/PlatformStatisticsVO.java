package com.example.demo.entity.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 平台运营统计（登录/注册页展示用）。
 *
 * <p>全部字段均为<b>实时聚合自业务库</b>，不存在写死的展示值：
 * 口径见 {@code IndexStatisticsServiceImpl} 的注释。
 * 页面若取不到数据应隐藏该模块，而不是回退成假数字。</p>
 */
@Data
public class PlatformStatisticsVO {

    /** 注册用户总数（user 表有效行） */
    private Long userCount;

    /** 已通过借款认证的借款人数量 */
    private Long borrowerCount;

    /** 投资人数量（user_type=1 或已通过投资认证） */
    private Long investorCount;

    /** 累计发标笔数 */
    private Long lendCount;

    /** 募资中的标的数量（lend.status=1） */
    private Long publishingCount;

    /** 累计发标金额（Σ lend.amount） */
    private BigDecimal publishAmount;

    /** 累计撮合成交金额（Σ lend.invest_amount） */
    private BigDecimal matchedAmount;

    /** 平均年化利率（%，Σ 平均值取自真实标的） */
    private BigDecimal avgYearRate;

    /** 最低年化利率（%） */
    private BigDecimal minYearRate;

    /** 最高年化利率（%） */
    private BigDecimal maxYearRate;

    /** 平均投资期限（月） */
    private BigDecimal avgPeriod;

    /** 还款计划总期数 */
    private Long returnPlanCount;

    /** 已按期还款期数 */
    private Long returnedCount;

    /** 逾期期数 */
    private Long overdueCount;

    /**
     * 按时履约率（%）= 已还期数 /（已还期数 + 逾期期数）。
     * 无到期记录时为 null，前端按"暂无数据"处理，而不是显示 100%。
     */
    private BigDecimal onTimeRate;
}
