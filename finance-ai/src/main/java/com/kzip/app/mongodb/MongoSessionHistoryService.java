package com.kzip.app.mongodb;

import com.kzip.app.mapper.ChatMemoryMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 会话历史 · MongoDB 存储服务（迁移替换 MySQL chat_session / chat_message）。
 * 对话流程（SessionService）与本模块（会话历史）统一从这里读写。
 */
@Service
public class MongoSessionHistoryService {

    private static final Logger log = LoggerFactory.getLogger(MongoSessionHistoryService.class);

    /** 记忆 conversationId 前缀（与 ChatController 中 "session-" + sessionId 保持一致） */
    private static final String MEMORY_PREFIX = "session-";
    /** 意图识别专用记忆后缀（与 ReActService.INTENT_MEMORY_SUFFIX 保持一致） */
    private static final String INTENT_MEMORY_SUFFIX = ":intent";

    private static final int TITLE_MAX_LEN = 30;

    private final ChatSessionDocRepository sessionRepo;
    private final ChatMessageDocRepository messageRepo;
    private final MongoTemplate mongoTemplate;
    private final ChatMemoryMapper chatMemoryMapper;

    /**
     * 手动自增会话 id 序列。
     * 说明：Spring Data MongoDB 只能为 String/ObjectId 类型的 @Id 自动生成 id，
     * Long 类型需手动赋值，否则 save() 会抛 "Cannot autogenerate id of type java.lang.Long"。
     * 这里用 AtomicLong 模拟自增，并在启动时以现有最大 id 初始化，避免重启后 id 冲突。
     */
    private final AtomicLong idSequence = new AtomicLong(0);

    public MongoSessionHistoryService(ChatSessionDocRepository sessionRepo,
                                      ChatMessageDocRepository messageRepo,
                                      MongoTemplate mongoTemplate,
                                      ChatMemoryMapper chatMemoryMapper) {
        this.sessionRepo = sessionRepo;
        this.messageRepo = messageRepo;
        this.mongoTemplate = mongoTemplate;
        this.chatMemoryMapper = chatMemoryMapper;
    }

    /** 启动时以现有最大会话 id 初始化自增序列，保证重启后 id 单调递增不冲突 */
    @PostConstruct
    public void initIdSequence() {
        try {
            List<ChatSessionDoc> maxDoc = mongoTemplate.find(
                    new Query().with(Sort.by(Sort.Direction.DESC, "id")).limit(1),
                    ChatSessionDoc.class);
            if (!maxDoc.isEmpty() && maxDoc.get(0).getId() != null) {
                idSequence.set(maxDoc.get(0).getId());
            }
        } catch (Exception e) {
            // Mongo 未就绪时忽略，序列保持 0 起步
        }
    }

    /** 创建会话，返回会话 id */
    public Long createSession(Long userId, String userPhone, String userName, String title) {
        ChatSessionDoc doc = new ChatSessionDoc();
        doc.setId(idSequence.incrementAndGet());
        doc.setUserId(userId);
        doc.setUserPhone(userPhone != null ? userPhone : "");
        doc.setUserName(userName != null ? userName : "");
        doc.setTitle(title != null ? title : "");
        doc.setModelName("qwen3.8-max");
        doc.setKbName("");
        doc.setMessageCount(0);
        doc.setStatus(0);
        doc.setCreateTime(LocalDateTime.now());
        doc.setUpdateTime(LocalDateTime.now());
        ChatSessionDoc saved = sessionRepo.save(doc);
        return saved.getId();
    }

    /** 保存用户消息 + 会话计数自增 + 首条作为标题 */
    @Transactional
    public void saveUserMessage(Long sessionId, Long userId, String content) {
        saveMessage(sessionId, userId, "user", content, null);
        if (sessionId != null && content != null && !content.trim().isEmpty()) {
            String title = content.trim();
            if (title.length() > TITLE_MAX_LEN) {
                title = title.substring(0, TITLE_MAX_LEN) + "...";
            }
            mongoTemplate.updateFirst(
                    Query.query(Criteria.where("_id").is(sessionId).and("title").is("")),
                    Update.update("title", title).set("updateTime", LocalDateTime.now()),
                    ChatSessionDoc.class);
        }
    }

    /** 保存 AI 回答 + 会话计数自增 */
    @Transactional
    public void saveAssistantMessage(Long sessionId, Long userId, String content, String chunkIds) {
        saveMessage(sessionId, userId, "assistant", content, chunkIds);
    }

    private void saveMessage(Long sessionId, Long userId, String role, String content, String chunkIds) {
        try {
            ChatMessageDoc msg = new ChatMessageDoc();
            msg.setSessionId(sessionId);
            msg.setUserId(userId);
            msg.setRole(role);
            msg.setContent(content);
            if (chunkIds != null) {
                msg.setChunkIds(chunkIds);
            }
            msg.setCreateTime(LocalDateTime.now());
            messageRepo.save(msg);

            // 会话计数原子自增
            mongoTemplate.updateFirst(
                    Query.query(Criteria.where("_id").is(sessionId)),
                    new Update().inc("messageCount", 1).set("updateTime", LocalDateTime.now()),
                    ChatSessionDoc.class);
        } catch (Exception e) {
            // 消息落库可降级，不打断对话主流程
            logSaveError(sessionId, role, e);
        }
    }

    private void logSaveError(Long sessionId, String role, Exception e) {
        // 简化：异常仅吞掉避免打断对话；如需日志可在此接入 slf4j
    }

    /** 分页查询会话（管理端"会话历史"），支持关键词 + 状态筛选 */
    public Map<String, Object> pageSessions(Integer pageNum, Integer pageSize, String keyword, Integer status) {
        int page = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int size = pageSize == null || pageSize < 1 ? 10 : pageSize;
        Criteria criteria = new Criteria();
        if (status != null) {
            criteria.and("status").is(status);
        }
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            criteria.andOperator(
                    new Criteria().orOperator(
                            Criteria.where("userName").regex(kw, "i"),
                            Criteria.where("userPhone").regex(kw, "i"),
                            Criteria.where("title").regex(kw, "i")));
        }
        Query query = new Query(criteria).with(Sort.by(Sort.Direction.DESC, "updateTime"))
                .skip((long) (page - 1) * size).limit(size);
        long total = mongoTemplate.count(new Query(criteria), ChatSessionDoc.class);
        List<ChatSessionDoc> list = mongoTemplate.find(query, ChatSessionDoc.class);
        return Map.of("list", list, "total", total, "current", page, "size", size);
    }

    /** 按用户分页（前台历史会话侧边栏） */
    public Map<String, Object> pageByUserId(Long userId, Integer pageNum, Integer pageSize) {
        int page = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int size = pageSize == null || pageSize < 1 ? 20 : pageSize;
        Criteria criteria = Criteria.where("userId").is(userId);
        Query query = new Query(criteria).with(Sort.by(Sort.Direction.DESC, "updateTime"))
                .skip((long) (page - 1) * size).limit(size);
        long total = mongoTemplate.count(new Query(criteria), ChatSessionDoc.class);
        List<ChatSessionDoc> list = mongoTemplate.find(query, ChatSessionDoc.class);
        return Map.of("list", list, "total", total, "current", page, "size", size);
    }

    /** 会话详情 + 消息列表 */
    public Map<String, Object> detail(Long sessionId) {
        Optional<ChatSessionDoc> opt = sessionRepo.findById(sessionId);
        if (opt.isEmpty()) {
            return null;
        }
        List<ChatMessageDoc> messages = messageRepo.findBySessionIdOrderByCreateTimeAsc(sessionId);
        return Map.of("session", opt.get(), "messages", messages);
    }

    /** 会话是否存在（用于前端持有已删除 sessionId 时自动重建新会话） */
    public boolean sessionExists(Long id) {
        if (id == null) {
            return false;
        }
        try {
            return sessionRepo.existsById(id);
        } catch (Exception e) {
            log.warn("会话存在性校验失败: sessionId={}, error={}", id, e.getMessage());
            return false;
        }
    }

    /** 关闭会话 */
    public boolean closeSession(Long id) {
        return mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(id)),
                Update.update("status", 1).set("updateTime", LocalDateTime.now()),
                ChatSessionDoc.class).getModifiedCount() > 0;
    }

    /**
     * 删除会话：MongoDB 会话 + 消息，以及 MySQL 多轮上下文记忆，三处一并清空。
     *
     * <p>说明：AI 的多轮上下文记忆存在 MySQL 的 chat_memory 表，
     * conversationId = "session-{id}"（意图识别另有一份 "session-{id}:intent"）。
     * 若不同步删除，会话重新以同一 id 提问时会读到已删除会话的残余记忆。
     */
    public boolean deleteSession(Long id) {
        if (id == null) {
            return false;
        }
        // 1. MongoDB 消息 + 会话
        messageRepo.deleteBySessionId(id);
        sessionRepo.deleteById(id);
        // 2. MySQL 记忆（失败不影响会话已删除的结果，仅告警）
        deleteMemory(id);
        return true;
    }

    /** 删除该会话关联的 MySQL 记忆（主记忆 + 意图记忆） */
    private void deleteMemory(Long sessionId) {
        String base = MEMORY_PREFIX + sessionId;
        try {
            int n1 = chatMemoryMapper.deleteByConversationId(base);
            int n2 = chatMemoryMapper.deleteByConversationId(base + INTENT_MEMORY_SUFFIX);
            log.info("会话记忆清理完成: sessionId={}, 主记忆{}条, 意图记忆{}条", sessionId, n1, n2);
        } catch (Exception e) {
            log.warn("会话记忆清理失败(会话已从MongoDB删除): sessionId={}, error={}", sessionId, e.getMessage());
        }
    }
}