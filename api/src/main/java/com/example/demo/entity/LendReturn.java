package com.example.demo.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 借款人还款计划实体（映射 lend_return 表）
 */
@Data
@TableName("lend_return")
public class LendReturn {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("lend_id")
    private Long lendId;

    @TableField("borrow_info_id")
    private Long borrowInfoId;

    /** 还款编号 */
    @TableField("return_no")
    private String returnNo;

    /** 借款人ID */
    @TableField("user_id")
    private Long userId;

    /** 标的总金额 */
    @TableField("amount")
    private BigDecimal amount;

    /** 剩余本金 */
    @TableField("base_amount")
    private BigDecimal baseAmount;

    /** 当前期数（从1开始） */
    @TableField("current_period")
    private Integer currentPeriod;

    @TableField("lend_year_rate")
    private BigDecimal lendYearRate;

    /** 还款方式 1等额本息 2等额本金 3每月还息到期还本 4一次还本付息 */
    @TableField("return_method")
    private Integer returnMethod;

    /** 本期本金 */
    @TableField("principal")
    private BigDecimal principal;

    /** 本期利息 */
    @TableField("interest")
    private BigDecimal interest;

    /** 本期应还总额（本金+利息） */
    @TableField("total")
    private BigDecimal total;

    /** 手续费 */
    @TableField("fee")
    private BigDecimal fee;

    /** 应还日期 */
    @TableField("return_date")
    private LocalDate returnDate;

    /** 实际还款时间 */
    @TableField("real_return_time")
    private LocalDateTime realReturnTime;

    /** 是否逾期 0否 1是 */
    @TableField("is_overdue")
    private Boolean overdue;

    /** 逾期金额（含罚息） */
    @TableField("overdue_total")
    private BigDecimal overdueTotal;

    /** 是否最后一期 0否 1是 */
    @TableField("is_last")
    private Boolean last;

    /** 状态 0未还 1已还 2逾期 */
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
