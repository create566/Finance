package com.example.demo.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("lend_settle_record")
public class LendSettleRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("lend_id")
    private Long lendId;

    @TableField("lend_no")
    private String lendNo;

    @TableField("borrower_id")
    private Long borrowerId;

    @TableField("total_principal")
    private BigDecimal totalPrincipal;

    @TableField("total_interest")
    private BigDecimal totalInterest;

    @TableField("total_penalty")
    private BigDecimal totalPenalty;

    @TableField("settle_time")
    private LocalDateTime settleTime;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
