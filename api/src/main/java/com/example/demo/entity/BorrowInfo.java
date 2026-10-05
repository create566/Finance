package com.example.demo.entity;


import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Accessors(chain = true)
@TableName("borrow_info")
public class BorrowInfo implements Serializable {

    private  static final   long serialVersionUID = 1L;

    /**
     * 编号
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 借款用户id
     */
    @TableField("user_id")
    private Long userId;

    /**
     * 借款金额
     */
    @TableField("amount")
    private BigDecimal amount;

    /**
     * 借款期限
     */
    @TableField("period")
    private Integer period;

    /**
     * 年化利率
     */
    @TableField("borrow_year_rate")
    private BigDecimal borrowYearRate;

    /**
     * 还款方式 1-等额本息 2-等额本金 3-每月还息一次还本 4-一次还本
     */
    @TableField("return_method")
    private Integer returnMethod;

    /**
     * 资金用途
     */
    @TableField("money_use")
    private Integer moneyUse;

    /**
     * 状态（0：未提交，1：审核中， 2：审核通过， -1：审核不通过）
     */
    @TableField("status")
    private Integer status;

    /**
     * 审核管理员ID（审核通过/拒绝时写入，未审核为 null）。
     * <p>内部审计字段，{@code @JsonIgnore} 不对前台暴露；后台列表在 AdminBorrowInfoController 里显式回填。
     */
    @TableField("check_admin_id")
    @JsonIgnore
    private Long checkAdminId;

    /**
     * 审核时间（审核通过/拒绝时写入，未审核为 null）。
     * <p>内部审计字段，不对前台暴露。
     */
    @TableField("check_time")
    @JsonIgnore
    private LocalDateTime checkTime;

    /**
     * 创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;

    /**
     * 逻辑删除(1:已删除，0:未删除)
     */
    @TableLogic
    @TableField("is_deleted")
    private Boolean deleted;



}
