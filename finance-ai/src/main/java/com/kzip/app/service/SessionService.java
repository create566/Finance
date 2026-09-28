package com.kzip.app.service;

import com.kzip.app.entity.ChatMessage;
import com.kzip.app.entity.ChatSession;
import com.kzip.app.mapper.ChatSessionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class SessionService {

    private static final Logger log = LoggerFactory.getLogger(SessionService.class);

    @Autowired
    private ChatSessionService chatSessionService;

    @Autowired
    private ChatMessageService chatMessageService;

    @Autowired
    private ChatSessionMapper chatSessionMapper;

    public Long createSession(Long userId, String userPhone, String userName, String title) {
        try {
            ChatSession session = new ChatSession();
            session.setUserId(userId);
            session.setUserPhone(userPhone != null ? userPhone : "");
            session.setUserName(userName != null ? userName : "");
            session.setTitle(title != null ? title : "");
            // 与 CommonConfiguration.answerClient 实际配置的模型保持一致，避免展示与真实不符
            session.setModelName("qwen3.8-max");
            session.setKbName("");

            ChatSession created = chatSessionService.createSession(session);
            log.info("会话创建成功: sessionId={}, userId={}", created.getId(), userId);
            return created.getId();
        } catch (Exception e) {
            // 失败必须向上抛出：返回 null 会导致 controller 用 null 继续
            // saveUserMessage / 写响应头，表现为"静默失败"难以排查
            log.error("创建会话失败: userId={}", userId, e);
            throw new IllegalStateException("创建会话失败: " + e.getMessage(), e);
        }
    }

    public void saveUserMessage(Long sessionId, Long userId, String content) {
        saveMessage(sessionId, userId, "user", content, null);
    }

    public void saveAssistantMessage(Long sessionId, Long userId, String content, String chunkIds) {
        saveMessage(sessionId, userId, "assistant", content, chunkIds);
    }

    private void saveMessage(Long sessionId, Long userId, String role, String content, String chunkIds) {
        try {
            ChatMessage message = new ChatMessage();
            message.setSessionId(sessionId);
            message.setUserId(userId);
            message.setRole(role);
            message.setContent(content);
            if (chunkIds != null) {
                message.setChunkIds(chunkIds);
            }

            chatMessageService.saveMessage(message);
            // 修复：同步自增会话消息计数（此前 chat_session.message_count 恒为 0，管理端消息数列失效）
            if (sessionId != null) {
                chatSessionMapper.incrementMessageCount(sessionId);
            }
            log.debug("消息保存成功: sessionId={}, role={}", sessionId, role);
        } catch (Exception e) {
            // 消息落库是可降级操作：不打断对话主流程，但保留完整堆栈便于排查
            log.error("保存消息失败: sessionId={}, role={}", sessionId, role, e);
        }
    }

    public void closeSession(Long sessionId) {
        try {
            chatSessionService.closeSession(sessionId);
            log.info("会话关闭成功: sessionId={}", sessionId);
        } catch (Exception e) {
            log.warn("关闭会话失败: {}", e.getMessage());
        }
    }
}