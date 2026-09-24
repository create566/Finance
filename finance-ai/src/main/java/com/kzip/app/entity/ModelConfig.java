package com.kzip.app.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_model_config")
public class ModelConfig {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String name;

    private String provider;

    private String modelType;

    private String modelName;

    private String apiUrl;

    private String apiKey;

    private Integer maxTokens;

    private Double temperature;

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