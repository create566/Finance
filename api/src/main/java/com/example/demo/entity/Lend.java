package com.example.demo.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 标的实体（映射数据库 lend 表真实结构）
 */
@Data
@TableName("lend")
public class Lend {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    /** 关联借款申请ID */
    @TableField("borrow_info_id")
    private Long borrowInfoId;

    @TableField("lend_no")
    private String lendNo;

    @TableField("title")
    private String title;

    @TableField("amount")
    private BigDecimal amount;

    @TableField("period")
    private Integer period;

    /** 年化利率(百分数,7.0表示7%) */
    @TableField("lend_year_rate")
    private BigDecimal lendYearRate;

    /** 平台服务费率 */
    @TableField("service_rate")
    private BigDecimal serviceRate;

    @TableField("return_method")
    private Integer returnMethod;

    /** 最低起投金额 */
    @TableField("lowest_amount")
    private BigDecimal lowestAmount;

    /** 已募集金额 */
    @TableField("invest_amount")
    private BigDecimal investAmount;

    /** 投资人数 */
    @TableField("invest_num")
    private Integer investNum;

    @TableField("publish_date")
    private LocalDateTime publishDate;

    @TableField("lend_start_date")
    private LocalDateTime lendStartDate;

    @TableField("lend_end_date")
    private LocalDateTime lendEndDate;

    @TableField("lend_info")
    private String lendInfo;

    /** 预期募集金额 */
    @TableField("expect_amount")
    private BigDecimal expectAmount;

    /** 实际募集金额 */
    @TableField("real_amount")
    private BigDecimal realAmount;

    @TableField("status")
    private Integer status;

    /** 审核时间 */
    @TableField("check_time")
    private LocalDateTime checkTime;

    /** 审核管理员ID */
    @TableField("check_admin_id")
    private Long checkAdminId;

    /** 放款时间 */
    @TableField("payment_time")
    private LocalDateTime paymentTime;

    /** 放款管理员ID */
    @TableField("payment_admin_id")
    private LocalDateTime paymentAdminId;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    @TableField(value = "is_deleted")
    private Integer deleted;
}
