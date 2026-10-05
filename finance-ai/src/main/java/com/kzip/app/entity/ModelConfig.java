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

    /**
     * 连通性测试结果：0-未测试/未通过，1-已通过。
     * 「对话模型选择」只允许绑定 test_status=1 的对话模型 —— 让"模型可用"这件事有可验证的依据，
     * 而不是靠管理员口头确认。任何影响连接结果的字段（地址/密钥/模型标识/类型）变更后会被重置为 0。
     */
    private Integer testStatus;

    /** 最近一次测试时间 */
    private LocalDateTime testTime;

    /** 最近一次测试结果说明（失败原因 / 成功耗时），便于管理员排查 */
    private String testMessage;

    /**
     * 正在引用该模型的「对话模型选择」场景名（逗号分隔），非持久化字段。
     * 管理端据此在删除/停用前就把"为什么不能删"摆出来，而不是点了才弹错误。
     * 含已禁用但未删除的绑定 —— 它们同样会阻止删除。
     */
    @TableField(exist = false)
    private String usedBy;

    /** 其中处于"启用中"的引用场景（只有这些会阻止停用） */
    @TableField(exist = false)
    private String activeUsedBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    @TableField(value = "is_deleted")
    private Integer deleted;
}