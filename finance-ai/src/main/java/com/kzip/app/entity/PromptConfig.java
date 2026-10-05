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

    /**
     * 非持久化字段：该条指令是否为其场景下运行时真正生效的指令（1=生效，0=未生效）。
     *
     * <p>规则与 {@code PromptService.refreshPrompts()} 完全一致：同一场景内优先取 is_default=1 的条目，
     * 没有默认则取 id 最小的一条；contentType=keywords 的关键词表不参与场景指令选择。
     *
     * <p>管理端此前只看得到"默认"标记，看不出同场景下到底哪条在跑，
     * 改了另一条却发现线上没变化——该字段用于把这个事实直接显示出来。
     */
    @TableField(exist = false)
    private Integer effective;
}