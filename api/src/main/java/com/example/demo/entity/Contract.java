package com.example.demo.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 合同实例实体（映射 contract 表）
 * <p>
 * 安全要点：
 * <ul>
 *   <li>{@code contentSnapshot} —— 变量渲染后的正文快照，签署后冻结，不随模板变化</li>
 *   <li>{@code contentHash} —— 正文 SHA-256，任何事后改库都会导致哈希对不上</li>
 *   <li>uk_biz(biz_type, biz_id, contract_type, is_deleted) —— 同一业务的同类合同不会重复生成</li>
 * </ul>
 */
@Data
@TableName("contract")
public class Contract {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 合同编号 */
    @TableField("contract_no")
    private String contractNo;

    /** 合同类型：LOAN / INVEST / PAYMENT */
    @TableField("contract_type")
    private String contractType;

    /** 业务对象类型：LEND / BORROW_INFO / LEND_ITEM */
    @TableField("biz_type")
    private String bizType;

    @TableField("biz_id")
    private Long bizId;

    @TableField("template_id")
    private Long templateId;

    /** 签署时使用的模板版本 */
    @TableField("template_version")
    private Integer templateVersion;

    /** 甲方 userId */
    @TableField("party_a_id")
    private Long partyAId;

    @TableField("party_a_name")
    private String partyAName;

    /** 乙方 userId（单方签署可为空） */
    @TableField("party_b_id")
    private Long partyBId;

    @TableField("party_b_name")
    private String partyBName;

    @TableField("amount")
    private BigDecimal amount;

    /** 年化利率（百分数） */
    @TableField("lend_year_rate")
    private BigDecimal lendYearRate;

    @TableField("period")
    private Integer period;

    @TableField("start_date")
    private LocalDate startDate;

    @TableField("end_date")
    private LocalDate endDate;

    /** 正文快照（渲染后冻结） */
    @TableField("content_snapshot")
    private String contentSnapshot;

    /** 正文 SHA-256 */
    @TableField("content_hash")
    private String contentHash;

    /** 0待签署 1已签署 2已作废 */
    @TableField("status")
    private Integer status;

    @TableField("sign_deadline")
    private LocalDateTime signDeadline;

    @TableField("revoke_reason")
    private String revokeReason;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    @TableField(value = "is_deleted")
    private Integer deleted;
}
