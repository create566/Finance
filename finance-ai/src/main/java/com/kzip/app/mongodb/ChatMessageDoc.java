package com.kzip.app.mongodb;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * MongoDB 会话消息文档（迁移自 MySQL chat_message）。
 * 每个会话的多轮对话记录，按 createTime 升序展示。
 */
@Data
@Document(collection = "chat_message")
public class ChatMessageDoc {

    @Id
    private String id;

    /** 所属会话 id（关联 ChatSessionDoc.id） */
    private Long sessionId;

    private Long userId;

    /** user / assistant */
    private String role;

    private String content;

    private String chunkIds;

    private LocalDateTime createTime;
}