package com.kzip.app.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_chat_model_binding")
public class ChatModelBinding {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String scene;

    private String sceneName;

    private Long modelId;

    private String modelName;

    private Long promptId;

    private String promptKey;

    private Long kbId;

    private String kbName;

    private Integer enabled;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    @TableField(value = "is_deleted")
    private Integer deleted;
}