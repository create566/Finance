package com.kzip.app.service;

import com.kzip.app.entity.PromptConfig;
import com.kzip.app.util.KeywordUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
            List<PromptConfig> list = promptConfigService.listAll(query, null);
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
            // 选择规则抽到 PromptSelector，保证与「Prompt 设置」页面展示的"场景默认"完全一致
            Map<String, PromptConfig> effectiveByScene = PromptSelector.pickEffectiveByScene(list);
            for (Map.Entry<String, PromptConfig> entry : effectiveByScene.entrySet()) {
                PromptConfig item = entry.getValue();
                configCache.put("scene:" + entry.getKey(), item);
                if (item.getSystemPrompt() != null) {
                    promptCache.put("scene:" + entry.getKey(), item.getSystemPrompt());
                }
            }
            // 按指令标识索引整条配置：供「对话模型选择」绑定具体指令后运行时原样取用
            for (PromptConfig item : list) {
                if (item.getPromptKey() != null && item.getStatus() != null && item.getStatus() == 1
                        && !isKeywordConfig(item)) {
                    configCache.put("key:" + item.getPromptKey(), item);
                }
            }
            log.info("提示词刷新成功, 共加载 {} 条, {} 个场景配置", list.size(), configCache.size());
            lastRefreshTime = LocalDateTime.now();
        } catch (Exception e) {
            log.error("提示词刷新失败, 使用缓存: {}", e.getMessage());
        }
    }

    private boolean isKeywordConfig(PromptConfig item) {
        return PromptSelector.isKeywordConfig(item);
    }

    public PromptConfig getConfigByScene(String scene) {
        return configCache.get("scene:" + scene);
    }

    /**
     * 按指令标识取整条配置（仅启用中的系统指令）。
     * 运行时的取用顺序是「对话模型选择里绑定的指令 → 该场景的默认指令」，
     * 绑定的那一步就靠它把 promptKey 还原成可用的配置。
     *
     * @return 未配置/已停用/是关键词表时返回 null，调用方回退场景默认值
     */
    public PromptConfig getConfigByKey(String promptKey) {
        if (promptKey == null || promptKey.isBlank()) {
            return null;
        }
        return configCache.get("key:" + promptKey.trim());
    }

    /**
     * 按指令标识取原文（关键词表也走这里：UNSAFE_KEYWORDS / HANDOFF_KEYWORDS）。
     * 取不到时返回空串而不是全局默认提示词 —— 关键词表缺失时应当表示"没有关键词"，
     * 而不是拿一段中文提示词去当关键词匹配。
     */
    public String getRawByKey(String promptKey) {
        String raw = promptCache.get(promptKey);
        return raw != null ? raw : "";
    }

    /**
     * 关键词表的词集合（已归一化、按 逗号/顿号/换行 切分）。
     * 内容安全拦截与转人工检测都基于它，规则集中在 {@link com.kzip.app.util.KeywordUtils}。
     */
    public Set<String> getKeywordTokens(String promptKey) {
        return KeywordUtils.split(getRawByKey(promptKey));
    }

    /**
     * Prompt 变更事件：管理端保存/删除后立即让缓存失效。
     * 用事件而不是直接注入，是为了避免 PromptService 与 PromptConfigService 互相依赖形成循环。
     */
    public record ChangedEvent(String reason) {
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onPromptChanged(ChangedEvent event) {
        log.info("Prompt 配置变更，立即刷新缓存：{}", event.reason());
        refreshPrompts();
    }
}