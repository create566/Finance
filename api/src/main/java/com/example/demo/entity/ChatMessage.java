package com.example.demo.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("chat_message")
public class ChatMessage {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long sessionId;

    private Long userId;

    private String role;

    private String content;

    private String chunkIds;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableLogic
    @TableField(value = "is_deleted")
    private Integer deleted;
}