package com.example.demo.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 交易流水实体
 * 对应数据库 trans_flow 表
 * 每一笔充值、提现、投标、回款都会在此表记录一条流水
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("trans_flow")
public class TransFlow implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 编号
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 用户id
     */
    @TableField("user_id")
    private Long userId;

    /**
     * 用户名称
     */
    @TableField("user_name")
    private String userName;

    /**
     * 交易单号（唯一索引，防止重复提交）
     */
    @TableField("trans_no")
    private String transNo;

    /**
     * 交易类型（1：充值 2：提现 3：投标 4：投资回款）
     */
    @TableField("trans_type")
    private Integer transType;

    /**
     * 交易类型名称（如"充值"、"提现"，方便前端直接展示）
     */
    @TableField("trans_type_name")
    private String transTypeName;

    /**
     * 交易金额
     */
    @TableField("trans_amount")
    private BigDecimal transAmount;

    /**
     * 备注
     */
    @TableField("memo")
    private String memo;

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
