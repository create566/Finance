package com.kzip.app.service;

import com.kzip.app.entity.PromptConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PromptService {

    private static final Logger log = LoggerFactory.getLogger(PromptService.class);

    @Autowired
    private PromptConfigService promptConfigService;

    private String cachedSystemPrompt = "请根据提供的上下文回答问题，不要自己猜测。";
    private final Map<String, String> promptCache = new ConcurrentHashMap<>();
    private LocalDateTime lastRefreshTime;

    @PostConstruct
    public void init() {
        refreshPrompts();
    }

    @Scheduled(fixedDelay = 300000)
    public void scheduledRefresh() {
        refreshPrompts();
    }

    public synchronized void refreshPrompts() {
        try {
            PromptConfig query = new PromptConfig();
            List<PromptConfig> list = promptConfigService.listAll(query);
            promptCache.clear();
            for (PromptConfig item : list) {
                if (item.getPromptKey() != null && item.getSystemPrompt() != null && item.getStatus() == 1) {
                    promptCache.put(item.getPromptKey(), item.getSystemPrompt());
                    if (item.getScene() != null) {
                        promptCache.put("scene:" + item.getScene(), item.getSystemPrompt());
                    }
                    if (item.getIsDefault() != null && item.getIsDefault() == 1) {
                        cachedSystemPrompt = item.getSystemPrompt();
                    }
                }
            }
            log.info("提示词刷新成功, 共加载 {} 条", list.size());
            lastRefreshTime = LocalDateTime.now();
        } catch (Exception e) {
            log.error("提示词刷新失败, 使用缓存: {}", e.getMessage());
        }
    }

    public String getSystemPromptByScene(String scene) {
        String prompt = promptCache.get("scene:" + scene);
        if (prompt != null) {
            return prompt;
        }
        return cachedSystemPrompt;
    }

    public String getSystemPromptByKey(String promptKey) {
        String prompt = promptCache.get(promptKey);
        return prompt != null ? prompt : cachedSystemPrompt;
    }

    public String getDefaultSystemPrompt() {
        return cachedSystemPrompt;
    }

    public Map<String, String> getAllPrompts() {
        return Collections.unmodifiableMap(promptCache);
    }
}