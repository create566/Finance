package com.kzip.app.memory;

import com.kzip.app.entity.ChatMemoryMessage;
import com.kzip.app.mapper.ChatMemoryMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 数据库持久化的 ChatMemoryRepository，替代 Spring AI 默认的 InMemoryChatMemoryRepository。
 *
 * 作用：让 MessageChatMemoryAdvisor 的上下文记忆落库，服务重启后仍可读取历史上下文，
 * 实现"可阅读上下文记忆"。replace 语义与 InMemoryChatMemoryRepository 保持一致
 * （MessageWindowChatMemory 每次以全量窗口调用 saveAll，故先删后插）。
 */
@Component
public class DbChatMemoryRepository implements ChatMemoryRepository {

    private static final Logger log = LoggerFactory.getLogger(DbChatMemoryRepository.class);

    private final ChatMemoryMapper chatMemoryMapper;

    public DbChatMemoryRepository(ChatMemoryMapper chatMemoryMapper) {
        this.chatMemoryMapper = chatMemoryMapper;
    }

    @Override
    public List<String> findConversationIds() {
        return chatMemoryMapper.selectDistinctConversationIds();
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        List<ChatMemoryMessage> rows = chatMemoryMapper.selectByConversationId(conversationId);
        List<Message> messages = new ArrayList<>(rows.size());
        for (ChatMemoryMessage row : rows) {
            messages.add(toMessage(row));
        }
        return messages;
    }

    @Override
    @Transactional
    public void saveAll(String conversationId, List<Message> messages) {
        // replace 语义：MessageWindowChatMemory 每次传入窗口内全量消息，先清后插保证一致性
        chatMemoryMapper.deleteByConversationId(conversationId);
        if (messages == null || messages.isEmpty()) {
            return;
        }
        for (int i = 0; i < messages.size(); i++) {
            Message msg = messages.get(i);
            ChatMemoryMessage row = new ChatMemoryMessage();
            row.setConversationId(conversationId);
            row.setMessageType(msg.getMessageType() != null ? msg.getMessageType().name() : "USER");
            row.setContent(msg.getText() != null ? msg.getText() : "");
            row.setSeq(i);
            chatMemoryMapper.insert(row);
        }
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        chatMemoryMapper.deleteByConversationId(conversationId);
    }

    private Message toMessage(ChatMemoryMessage row) {
        String type = row.getMessageType() != null ? row.getMessageType() : "USER";
        String content = row.getContent() != null ? row.getContent() : "";
        switch (type) {
            case "ASSISTANT":
                return new AssistantMessage(content);
            case "SYSTEM":
                return new SystemMessage(content);
            case "TOOL":
            case "USER":
            default:
                // TOOL 消息在客服场景极少且无独立重构价值，按 UserMessage 还原避免丢失文本
                return new UserMessage(content);
        }
    }
}
