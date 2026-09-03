package com.example.demo.entity;


import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("user_account")
public class UserAccount implements Serializable {

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
     * 帐户可用余额
     */
    @TableField("amount")
    private BigDecimal amount;

    /**
     * 冻结金额
     */
    @TableField("freeze_amount")
    private BigDecimal freezeAmount;

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

    /**
     * 版本号
     */
    @TableField("version")
    private Integer version;



}
