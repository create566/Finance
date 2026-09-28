package com.kzip.app.repository;


import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class InMemoryChatHistoryRepository implements ChatHistoryRepository {

    private static final int MAX_CHAT_IDS_PER_TYPE = 1000;

    private final Map<String, List<String>> chatHistory = new ConcurrentHashMap<>();

    @Override
    public void save(String type, String chatId) {
        List<String> chatIds = chatHistory.computeIfAbsent(type, k -> new ArrayList<>());
        if (chatIds.contains(chatId)) {
            return;
        }
        if (chatIds.size() >= MAX_CHAT_IDS_PER_TYPE) {
            chatIds.remove(0);
        }
        chatIds.add(chatId);
    }

    @Override
    public void delete(String type, String chatId) {
        chatHistory.computeIfPresent(type, (k, chatIds) -> {
            chatIds.remove(chatId);
            return chatIds.isEmpty() ? null : chatIds;
        });
    }

    @Override
    public List<String> getChatIds(String type) {
        return chatHistory.getOrDefault(type, List.of());
    }
}