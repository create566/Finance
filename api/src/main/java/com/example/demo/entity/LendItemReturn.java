package com.example.demo.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 投资人收益计划实体（映射 lend_item_return 表）
 * 每个投资人每一期对应一条记录
 */
@Data
@TableName("lend_item_return")
public class LendItemReturn {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 关联借款人还款计划ID */
    @TableField("lend_return_id")
    private Long lendReturnId;

    /** 关联投资记录ID */
    @TableField("lend_item_id")
    private Long lendItemId;

    @TableField("lend_id")
    private Long lendId;

    /** 投资人ID */
    @TableField("invest_user_id")
    private Long investUserId;

    /** 投资本金 */
    @TableField("invest_amount")
    private BigDecimal investAmount;

    /** 当前期数 */
    @TableField("current_period")
    private Integer currentPeriod;

    @TableField("lend_year_rate")
    private BigDecimal lendYearRate;

    @TableField("return_method")
    private Integer returnMethod;

    /** 本期应得本金 */
    @TableField("principal")
    private BigDecimal principal;

    /** 本期应得利息 */
    @TableField("interest")
    private BigDecimal interest;

    /** 本期应得总额 */
    @TableField("total")
    private BigDecimal total;

    @TableField("fee")
    private BigDecimal fee;

    @TableField("return_date")
    private LocalDate returnDate;

    @TableField("real_return_time")
    private LocalDateTime realReturnTime;

    @TableField("is_overdue")
    private Boolean overdue;

    @TableField("overdue_total")
    private BigDecimal overdueTotal;

    /** 状态 0未还 1已还 */
    @TableField("status")
    private Integer status;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    @TableField(value = "is_deleted")
    private Integer deleted;
}
