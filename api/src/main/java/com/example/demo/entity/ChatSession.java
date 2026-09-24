package com.example.demo.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("chat_session")
public class ChatSession {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String userPhone;

    private String userName;

    private String title;

    private Integer messageCount;

    private String modelName;

    private String kbName;

    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    @TableField(value = "is_deleted")
    private Integer deleted;
}