package com.kzip.app.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kzip.app.entity.ChatSession;
import com.kzip.app.mongodb.ChatSessionDoc;
import com.kzip.app.mongodb.MongoSessionHistoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 会话服务：会话/消息记录已迁移到 MongoDB（迁移替换 MySQL chat_session/chat_message）。
 * 对外方法签名保持不变，供 ChatController（对话流程）调用。
 */
@Service
public class SessionService {

    private static final Logger log = LoggerFactory.getLogger(SessionService.class);

    @Autowired
    private MongoSessionHistoryService mongoService;

    /** 列出某用户的会话（供前端"历史会话"侧边栏） */
    public IPage<ChatSession> listSessions(Long userId, Integer pageNum, Integer pageSize) {
        Map<String, Object> page = mongoService.pageByUserId(userId, pageNum, pageSize);
        List<ChatSession> records = toSessions((List<?>) page.get("list"));
        IPage<ChatSession> result = new Page<>();
        result.setRecords(records);
        // Mongo 返回的 total/current/size 可能是 Long 或 Integer，统一按 Number 安全取值，避免 Long→Integer 强转崩溃
        result.setTotal(num(page.get("total")).longValue());
        result.setCurrent(num(page.get("current")).intValue());
        result.setSize(num(page.get("size")).intValue());
        return result;
    }

    private static Number num(Object v) {
        if (v instanceof Number) return (Number) v;
        if (v == null) return 0;
        try {
            return Long.valueOf(String.valueOf(v));
        } catch (Exception e) {
            return 0;
        }
    }

    /** 创建会话，返回会话 id */
    public Long createSession(Long userId, String userPhone, String userName, String title) {
        try {
            Long id = mongoService.createSession(userId, userPhone, userName, title);
            log.info("会话创建成功(Mongo): sessionId={}, userId={}", id, userId);
            return id;
        } catch (Exception e) {
            log.error("创建会话失败: userId={}", userId, e);
            throw new IllegalStateException("创建会话失败: " + e.getMessage(), e);
        }
    }

    /** 保存用户消息 + 会话计数 + 首条作为标题 */
    public void saveUserMessage(Long sessionId, Long userId, String content) {
        mongoService.saveUserMessage(sessionId, userId, content);
    }

    /** 保存 AI 回答 + 会话计数 */
    public void saveAssistantMessage(Long sessionId, Long userId, String content, String chunkIds) {
        mongoService.saveAssistantMessage(sessionId, userId, content, chunkIds);
    }

    public void closeSession(Long sessionId) {
        try {
            mongoService.closeSession(sessionId);
            log.info("会话关闭成功(Mongo): sessionId={}", sessionId);
        } catch (Exception e) {
            log.warn("关闭会话失败: {}", e.getMessage());
        }
    }

    /** Mongo 会话文档 → 兼容 ChatSession 实体（保持外部契约） */
    @SuppressWarnings("unchecked")
    private List<ChatSession> toSessions(List<?> docs) {
        if (docs == null) return List.of();
        return docs.stream().map(this::toSession).collect(Collectors.toList());
    }

    /** 兼容 ChatSessionDoc 对象与 JSON Map 两种形态，统一转换 */
    private ChatSession toSession(Object o) {
        ChatSession s = new ChatSession();
        if (o instanceof ChatSessionDoc) {
            ChatSessionDoc d = (ChatSessionDoc) o;
            s.setId(d.getId());
            s.setUserId(d.getUserId());
            s.setUserPhone(d.getUserPhone());
            s.setUserName(d.getUserName());
            s.setTitle(d.getTitle());
            s.setMessageCount(d.getMessageCount() != null ? d.getMessageCount() : 0);
            s.setModelName(d.getModelName());
            s.setKbName(d.getKbName());
            s.setStatus(d.getStatus() != null ? d.getStatus() : 0);
            s.setCreateTime(d.getCreateTime());
            s.setUpdateTime(d.getUpdateTime());
            return s;
        }
        if (o instanceof Map) {
            Map<String, Object> m = (Map<String, Object>) o;
            s.setId(toLong(m.get("id")));
            s.setUserId(toLong(m.get("userId")));
            s.setUserPhone((String) m.get("userPhone"));
            s.setUserName((String) m.get("userName"));
            s.setTitle((String) m.get("title"));
            s.setMessageCount(m.get("messageCount") != null ? num(m.get("messageCount")).intValue() : 0);
            s.setModelName((String) m.get("modelName"));
            s.setKbName((String) m.get("kbName"));
            s.setStatus(m.get("status") != null ? num(m.get("status")).intValue() : 0);
            s.setCreateTime(toLdt(m.get("createTime")));
            s.setUpdateTime(toLdt(m.get("updateTime")));
            return s;
        }
        return s;
    }

    private static Long toLong(Object v) {
        if (v instanceof Number) return ((Number) v).longValue();
        if (v == null) return null;
        try {
            return Long.valueOf(String.valueOf(v));
        } catch (Exception e) {
            return null;
        }
    }

    private LocalDateTime toLdt(Object v) {
        if (v == null) return null;
        if (v instanceof LocalDateTime) return (LocalDateTime) v;
        try {
            return LocalDateTime.parse(String.valueOf(v));
        } catch (Exception e) {
            return null;
        }
    }
}