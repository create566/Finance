package com.example.demo.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 投资记录实体（映射 lend_item 表）
 */
@Data
@TableName("lend_item")
public class LendItem {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("lend_item_no")
    private String lendItemNo;

    @TableField("lend_id")
    private Long lendId;

    @TableField("invest_user_id")
    private Long investUserId;

    @TableField("invest_name")
    private String investName;

    @TableField("invest_amount")
    private BigDecimal investAmount;

    /** 年化利率（从标的拷贝，快照） */
    @TableField("lend_year_rate")
    private BigDecimal lendYearRate;

    @TableField("invest_time")
    private LocalDateTime investTime;

    @TableField("lend_start_date")
    private LocalDateTime lendStartDate;

    @TableField("lend_end_date")
    private LocalDateTime lendEndDate;

    /** 预期收益 */
    @TableField("expect_amount")
    private BigDecimal expectAmount;

    /** 实际收益 */
    @TableField("real_amount")
    private BigDecimal realAmount;

    /** 状态 0已投标 1已撤标 2已满标 3已结算 */
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
