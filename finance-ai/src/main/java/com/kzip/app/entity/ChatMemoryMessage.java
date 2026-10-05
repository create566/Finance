package com.kzip.app.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 会话记忆持久化实体，映射 chat_memory 表。
 * 与 chat_message（UI 会话历史）分离：本表专供 MessageChatMemoryAdvisor 读写，
 * 使上下文记忆在服务重启后依然可读。
 */
@Data
@TableName("chat_memory")
public class ChatMemoryMessage {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 与 ChatMemory 的 conversationId 对齐，如 "session-123" 或 "session-123:intent" */
    private String conversationId;

    /** 消息类型：USER / ASSISTANT / SYSTEM / TOOL */
    private String messageType;

    private String content;

    /** 会话内顺序，按 seq 还原上下文 */
    private Integer seq;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
