package com.kzip.app.service;

import com.kzip.app.entity.ChatMessage;
import com.kzip.app.entity.ChatSession;
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

    public Long createSession(Long userId, String userPhone, String userName, String title) {
        try {
            ChatSession session = new ChatSession();
            session.setUserId(userId);
            session.setUserPhone(userPhone != null ? userPhone : "");
            session.setUserName(userName != null ? userName : "");
            session.setTitle(title != null ? title : "");
            session.setModelName("qwen-plus");
            session.setKbName("");

            ChatSession created = chatSessionService.createSession(session);
            log.info("会话创建成功: sessionId={}, userId={}", created.getId(), userId);
            return created.getId();
        } catch (Exception e) {
            log.warn("创建会话失败: {}", e.getMessage());
            return null;
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
            log.debug("消息保存成功: sessionId={}, role={}", sessionId, role);
        } catch (Exception e) {
            log.warn("保存消息失败: {}", e.getMessage());
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