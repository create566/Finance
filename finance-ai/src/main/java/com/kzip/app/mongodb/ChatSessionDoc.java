package com.kzip.app.mongodb;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * MongoDB 会话历史文档（迁移自 MySQL chat_session）。
 * 会话记录由 SessionService 在对话时写入；会话历史模块从此读取。
 * 用 Long 作 _id，与前端数字 sessionId 契约保持一致。
 */
@Data
@Document(collection = "chat_session")
public class ChatSessionDoc {

    @Id
    private Long id;

    private Long userId;

    private String userPhone;

    private String userName;

    private String title;

    private Integer messageCount;

    private String modelName;

    private String kbName;

    /** 0=进行中 1=已结束 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}