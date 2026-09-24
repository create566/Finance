package com.kzip.app.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("prompt_config")
public class PromptConfig {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String promptKey;

    private String name;

    private String scene;

    private String contentType;

    private String systemPrompt;

    private Double temperature;

    private Integer maxTokens;

    private Double topP;

    private String model;

    private Integer isDefault;

    private Integer status;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    @TableField(value = "is_deleted")
    private Integer deleted;
}