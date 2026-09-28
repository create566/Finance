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
    private final Map<String, PromptConfig> configCache = new ConcurrentHashMap<>();
    private LocalDateTime lastRefreshTime;

    @PostConstruct
    public void init() {
        refreshPrompts();
    }

    @Scheduled(fixedDelay = 300000)
    public void scheduledRefresh() {
        refreshPrompts();
    }

    synchronized void refreshPrompts() {
        try {
            PromptConfig query = new PromptConfig();
            List<PromptConfig> list = promptConfigService.listAll(query);
            promptCache.clear();
            configCache.clear();
            for (PromptConfig item : list) {
                if (item.getPromptKey() != null && item.getSystemPrompt() != null && item.getStatus() == 1) {
                    promptCache.put(item.getPromptKey(), item.getSystemPrompt());
                    // 全局默认提示词：排除 keywords 类型的条目（关键词表不是提示词）
                    if (item.getIsDefault() != null && item.getIsDefault() == 1
                            && !isKeywordConfig(item)) {
                        cachedSystemPrompt = item.getSystemPrompt();
                    }
                }
            }
            // 场景配置单独处理：contentType=keywords 的条目是关键词表，不能作为场景 prompt / 模型配置。
            // 一个 scene 可能有多条配置（后写覆盖会随机胜出），优先 is_default=1 的条目
            for (PromptConfig item : list) {
                if (item.getScene() == null || item.getStatus() != 1 || isKeywordConfig(item)) {
                    continue;
                }
                PromptConfig exist = configCache.get("scene:" + item.getScene());
                if (exist == null || (item.getIsDefault() != null && item.getIsDefault() == 1)) {
                    configCache.put("scene:" + item.getScene(), item);
                    if (item.getSystemPrompt() != null) {
                        promptCache.put("scene:" + item.getScene(), item.getSystemPrompt());
                    }
                }
            }
            log.info("提示词刷新成功, 共加载 {} 条, {} 个场景配置", list.size(), configCache.size());
            lastRefreshTime = LocalDateTime.now();
        } catch (Exception e) {
            log.error("提示词刷新失败, 使用缓存: {}", e.getMessage());
        }
    }

    private boolean isKeywordConfig(PromptConfig item) {
        return item.getContentType() != null && item.getContentType().trim().equalsIgnoreCase("keywords");
    }

    public String getSystemPromptByScene(String scene) {
        String prompt = promptCache.get("scene:" + scene);
        if (prompt != null) {
            return prompt;
        }
        return cachedSystemPrompt;
    }

    public PromptConfig getConfigByScene(String scene) {
        return configCache.get("scene:" + scene);
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