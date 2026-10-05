package com.example.demo.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
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
    private LocalDate lendStartDate;

    @TableField("lend_end_date")
    private LocalDate lendEndDate;

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

    /**
     * 上架审核时间（发布募资时写入）。
     * <p>内部审计字段，{@code @JsonIgnore} 不对前台暴露；后台列表在 AdminLendController 里显式回填。
     */
    @TableField("check_time")
    @JsonIgnore
    private LocalDateTime checkTime;

    /**
     * 上架审核管理员ID（发布募资时写入）。
     * <p>内部审计字段，不对前台暴露（避免前台可枚举后台管理员主键）。
     */
    @TableField("check_admin_id")
    @JsonIgnore
    private Long checkAdminId;

    /** 放款时间（银行回调确认入账时写入，非管理员操作时间） */
    @TableField("payment_time")
    private LocalDateTime paymentTime;

    /**
     * 放款发起管理员ID（管理员点击"放款"生成银行表单时写入）。
     * <p>与 {@link #paymentTime} 语义不同：本字段=谁发起的，paymentTime=银行何时入账完成。
     * 两者可组合判断状态：本字段非空且 paymentTime 为空 → 已发起放款、待银行回调。
     */
    @TableField("payment_admin_id")
    @JsonIgnore
    private Long paymentAdminId;

    /**
     * 下架处置时间（管理端下架时写入）。
     * <p>每次下架都会覆盖写，语义是"最后一次处置的时间"，而非历史流水。
     */
    @TableField("offline_time")
    @JsonIgnore
    private LocalDateTime offlineTime;

    /**
     * 下架操作管理员ID（管理端下架时写入）。
     * <p>与 {@link #checkAdminId}（谁批准上架）、{@link #paymentAdminId}（谁发起放款）三者语义互斥，
     * 分别对应"进 → 放 → 退"三个高风险动作。<b>NULL 表示系统自动处置</b>（定时任务流标），
     * 非空才是人工操作。
     * <p>此处必填校验在 {@code LendServiceImpl#offline}：识别不到操作人时拒绝下架 ——
     * 匿名处置投资人冻结资金本身就是风险动作，宁可失败也不能留白。
     */
    @TableField("offline_admin_id")
    @JsonIgnore
    private Long offlineAdminId;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    @TableField(value = "is_deleted")
    private Integer deleted;
}