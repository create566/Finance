package com.example.demo.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 合同模板实体（映射 contract_template 表）
 * <p>
 * 正文用 ${变量名} 占位；模板变更只影响之后新生成的合同，已签合同靠 content_snapshot 冻结。
 */
@Data
@TableName("contract_template")
public class ContractTemplate {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 模板编号 */
    @TableField("template_no")
    private String templateNo;

    /** 合同类型：LOAN / INVEST / PAYMENT */
    @TableField("contract_type")
    private String contractType;

    @TableField("title")
    private String title;

    /** 正文模板 */
    @TableField("content_tpl")
    private String contentTpl;

    /** 变量清单（逗号分隔） */
    @TableField("variables")
    private String variables;

    @TableField("version")
    private Integer version;

    /** 1启用 0停用 */
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
