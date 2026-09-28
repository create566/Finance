package com.kzip.app.service;

import org.springframework.ai.openai.OpenAiChatOptions;
import com.kzip.app.entity.PromptConfig;
import com.kzip.app.tool.DateTimeTools;
import com.kzip.app.tool.KnowledgeTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class ReActService {

    private static final Logger log = LoggerFactory.getLogger(ReActService.class);

    private static final String DEFAULT_INTENT_MODEL = "qwen3.8-max";
    static final double DEFAULT_INTENT_TEMP = 0.0;
    static final String DEFAULT_ANSWER_MODEL = "qwen3.8-max";
    static final double DEFAULT_ANSWER_TEMP = 0.3;

    /**
     * Phase 1（意图路由）使用独立的记忆 key，与 Phase 2（回答生成）隔离：
     * - 避免意图阶段的工具调用中间产物写入用户会话记忆，污染多轮上下文；
     * - Phase 1 仍保留历史，便于理解"还款方式呢"这类省略主语的追问。
     */
    private static final String INTENT_MEMORY_SUFFIX = ":intent";

    private final ChatClient intentClient;
    private final ChatClient answerClient;
    private final PromptService promptService;

    public ReActService(ChatClient intentClient,
                        ChatClient answerClient,
                        PromptService promptService) {
        this.intentClient = intentClient;
        this.answerClient = answerClient;
        this.promptService = promptService;
    }

    public String chat(String question, String chatId) {
        log.info("ReAct 同步聊天: chatId={}, question={}", chatId,
                question.substring(0, Math.min(30, question.length())));

        PromptConfig intentCfg = promptService.getConfigByScene("intent");
        String intentPrompt = getIntentPrompt(intentCfg);
        KnowledgeTools.resetToolCalled();
        DateTimeTools.resetToolCalled();

        String turboOutput;
        try {
            var call = intentClient.prompt()
                    .system(intentPrompt)
                    .user(question)
                    .advisors(a -> a.param("chat_memory_conversation_id", chatId + INTENT_MEMORY_SUFFIX));
            call = applyIntentOptions(call, intentCfg);
            turboOutput = call.call().content();
        } catch (Exception e) {
            log.error("Phase 1 turbo调用失败: {}", e.getMessage());
            return "抱歉，AI服务暂时不可用，请稍后再试。";
        }

        boolean toolCalled = KnowledgeTools.wasToolCalled() || DateTimeTools.wasToolCalled();
        log.info("Phase 1 完成: toolCalled={}", toolCalled);

        if (!toolCalled) {
            return polishWithPlus(question, turboOutput, chatId);
        }
        return generateWithRag(question, chatId, resolveRagContext(turboOutput));
    }

    public Flux<String> chatStream(String question, String chatId) {
        log.info("ReAct 流式聊天: chatId={}, question={}", chatId,
                question.substring(0, Math.min(30, question.length())));

        PromptConfig intentCfg = promptService.getConfigByScene("intent");
        String intentPrompt = getIntentPrompt(intentCfg);
        KnowledgeTools.resetToolCalled();
        DateTimeTools.resetToolCalled();

        String turboOutput;
        try {
            var call = intentClient.prompt()
                    .system(intentPrompt)
                    .user(question)
                    .advisors(a -> a.param("chat_memory_conversation_id", chatId + INTENT_MEMORY_SUFFIX));
            call = applyIntentOptions(call, intentCfg);
            turboOutput = call.call().content();
        } catch (Exception e) {
            log.error("Phase 1 turbo流式调用失败: {}", e.getMessage());
            return Flux.just("抱歉，AI服务暂时不可用，请稍后再试。");
        }

        boolean toolCalled = KnowledgeTools.wasToolCalled() || DateTimeTools.wasToolCalled();
        log.info("Phase 1 流式完成: toolCalled={}", toolCalled);

        if (toolCalled) {
            PromptConfig ragCfg = promptService.getConfigByScene("rag");
            // RAG 上下文放 system（不会写入记忆），user 只放干净问题（会写入记忆），保证多轮上下文不被参考资料污染
            return applyAnswerOptions(answerClient.prompt()
                    .system(buildRagSystemPrompt(ragCfg, resolveRagContext(turboOutput)))
                    .user(question)
                    .advisors(a -> a.param("chat_memory_conversation_id", chatId)), ragCfg)
                    .stream()
                    .content();
        }

        PromptConfig chatCfg = promptService.getConfigByScene("chat");
        return applyAnswerOptions(answerClient.prompt()
                .system(getChatPrompt(chatCfg))
                .user(question)
                .advisors(a -> a.param("chat_memory_conversation_id", chatId)), chatCfg)
                .stream()
                .content();
    }

    private String polishWithPlus(String question, String turboOutput, String chatId) {
        PromptConfig chatCfg = promptService.getConfigByScene("chat");
        try {
            var call = answerClient.prompt()
                    .system(getChatPrompt(chatCfg))
                    .user(question)
                    .advisors(a -> a.param("chat_memory_conversation_id", chatId));
            call = applyAnswerOptions(call, chatCfg);
            return call.call().content();
        } catch (Exception e) {
            log.warn("Phase 2 plus润色失败，使用turbo原始回答: {}", e.getMessage());
            return turboOutput;
        }
    }

    private String generateWithRag(String question, String chatId, String context) {
        PromptConfig ragCfg = promptService.getConfigByScene("rag");
        try {
            // 上下文放 system（不写入记忆），user 只放干净问题（写入记忆），conversationId 指向会话记忆
            return applyAnswerOptions(answerClient.prompt()
                    .system(buildRagSystemPrompt(ragCfg, context))
                    .user(question)
                    .advisors(a -> a.param("chat_memory_conversation_id", chatId)), ragCfg)
                    .call()
                    .content();
        } catch (Exception e) {
            log.error("Phase 2 RAG生成失败: {}", e.getMessage());
            return "抱歉，知识库查询暂时不可用，请稍后再试。";
        }
    }

    /**
     * 组装 RAG 阶段的 system 提示词：基础提示 + 参考资料。
     * 参考资料放在 system 消息中，MessageChatMemoryAdvisor 不会持久化 system，
     * 因此向量片段不会污染多轮会话记忆。
     */
    private String buildRagSystemPrompt(PromptConfig cfg, String context) {
        return getRagPrompt(cfg) + "\n\n参考资料:\n" + context;
    }

    /**
     * 优先使用工具检索到的知识库原文片段（语义最忠实），
     * 兜底使用 Phase 1 模型对工具结果的总结输出。
     */
    private String resolveRagContext(String turboOutput) {
        String toolContext = KnowledgeTools.getLastSearchContext();
        return (toolContext != null && !toolContext.isBlank()) ? toolContext : turboOutput;
    }

    private ChatClient.ChatClientRequestSpec applyIntentOptions(
            ChatClient.ChatClientRequestSpec spec, PromptConfig cfg) {
        if (cfg != null && (cfg.getModel() != null || cfg.getTemperature() != null)) {
            return spec.options(OpenAiChatOptions.builder()
                    .model(cfg.getModel() != null ? cfg.getModel() : DEFAULT_INTENT_MODEL)
                    .temperature(cfg.getTemperature() != null
                            ? cfg.getTemperature() : DEFAULT_INTENT_TEMP)
                    .build());
        }
        return spec;
    }

    private ChatClient.ChatClientRequestSpec applyAnswerOptions(
            ChatClient.ChatClientRequestSpec spec, PromptConfig cfg) {
        if (cfg != null && (cfg.getModel() != null || cfg.getTemperature() != null)) {
            return spec.options(OpenAiChatOptions.builder()
                    .model(cfg.getModel() != null ? cfg.getModel() : DEFAULT_ANSWER_MODEL)
                    .temperature(cfg.getTemperature() != null
                            ? cfg.getTemperature() : DEFAULT_ANSWER_TEMP)
                    .build());
        }
        return spec;
    }

    private String getIntentPrompt(PromptConfig cfg) {
        if (cfg != null && cfg.getSystemPrompt() != null && !cfg.getSystemPrompt().isEmpty()) {
            return cfg.getSystemPrompt();
        }
        return """
                你是金融客服的意图路由器，判断用户问题类型并正确调用工具：
                - 问候/闲聊/你是谁 → 直接回答，不调工具
                - 借款利率/还款方式/贷款政策等专业知识 → 调用 searchKnowledgeBase
                - 项目内容/文档信息/知识库资料/系统功能 → 调用 searchKnowledgeBase
                - 时间/日期查询 → 调用 getCurrentDateTime
                一次对话不重复调用同一工具。
                """;
    }

    private String getChatPrompt(PromptConfig cfg) {
        if (cfg != null && cfg.getSystemPrompt() != null && !cfg.getSystemPrompt().isEmpty()) {
            return cfg.getSystemPrompt();
        }
        return "你是汇泽金融智能客服小汇，请用友好、专业的态度回答用户问题。";
    }

    private String getRagPrompt(PromptConfig cfg) {
        if (cfg != null && cfg.getSystemPrompt() != null && !cfg.getSystemPrompt().isEmpty()) {
            return cfg.getSystemPrompt();
        }
        return "你是知识库问答专家，严格根据以下参考资料回答用户问题，不要编造资料中没有的信息。";
    }
}