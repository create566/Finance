-- AI 智能客服会话记忆持久化表
-- 用途：替代 InMemoryChatMemoryRepository，让 MessageChatMemoryAdvisor 的上下文记忆
--       在 finance-ai 服务重启后依然可读，实现"可阅读上下文记忆"。
-- 说明：与 chat_message（会话历史/UI 展示）分离，避免与 SessionService 双写冲突。
--       conversation_id 与 ChatMemory 的 conversationId 对齐（如 "session-123"、"session-123:intent"）。
CREATE TABLE IF NOT EXISTS chat_memory (
  id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  conversation_id VARCHAR(128) NOT NULL                COMMENT '会话ID(与ChatMemory conversationId对齐)',
  message_type    VARCHAR(32)  NOT NULL                COMMENT '消息类型 USER/ASSISTANT/SYSTEM/TOOL',
  content         MEDIUMTEXT                           COMMENT '消息文本内容',
  seq             INT          NOT NULL                COMMENT '会话内顺序，用于按序还原上下文',
  create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  KEY idx_conversation_seq (conversation_id, seq)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI会话记忆持久化(MessageChatMemoryAdvisor)';
