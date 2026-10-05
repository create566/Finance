package com.kzip.app.service;

import org.springframework.ai.openai.OpenAiChatOptions;
import com.kzip.app.entity.ChatModelBinding;
import com.kzip.app.entity.PromptConfig;
import com.kzip.app.tool.DateTimeTools;
import com.kzip.app.tool.KnowledgeTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.concurrent.Callable;

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
    private final ChatModelBindingService chatModelBindingService;

    public ReActService(ChatClient intentClient,
                        ChatClient answerClient,
                        PromptService promptService,
                        ChatModelBindingService chatModelBindingService) {
        this.intentClient = intentClient;
        this.answerClient = answerClient;
        this.promptService = promptService;
        this.chatModelBindingService = chatModelBindingService;
    }

    /**
     * 用户端未在会话里指定知识库时，用「对话模型选择 · 知识库问答」绑定的知识库兜底。
     * 修复断链：绑定表的 kb_id 此前只存不读，管理员配的检索范围对运行时毫无影响。
     * 用户端的显式选择优先级更高（用户知道自己要问哪个库）。
     */
    private void applyDefaultKbIfAbsent() {
        if (KnowledgeTools.getCurrentKbId() != null) {
            return;
        }
        try {
            ChatModelBinding binding = chatModelBindingService.getByScene("rag");
            if (binding != null && binding.getEnabled() != null && binding.getEnabled() == 1
                    && binding.getKbId() != null && binding.getKbId() > 0) {
                KnowledgeTools.setCurrentKbId(binding.getKbId());
                log.info("未指定知识库，使用「知识库问答」绑定兜底: kbId={}", binding.getKbId());
            }
        } catch (Exception e) {
            log.warn("读取绑定知识库失败(不限定检索范围): {}", e.getMessage());
        }
    }

    public String chat(String question, String chatId) {
        log.info("ReAct 同步聊天: chatId={}, question={}", chatId,
                question.substring(0, Math.min(30, question.length())));

        // 提示词取用：「对话模型选择」中 intent 场景绑定的指令优先，未绑定才用 Prompt 设置的场景默认
        PromptConfig intentCfg = resolveScenePrompt("intent");
        String intentPrompt = getIntentPrompt(intentCfg);
        applyDefaultKbIfAbsent();
        KnowledgeTools.resetToolCalled();
        DateTimeTools.resetToolCalled();

        try {
            String turboOutput;
            try {
                turboOutput = retry(() -> {
                    var call = intentClient.prompt()
                            .system(intentPrompt)
                            .user(question)
                            .advisors(a -> a.param("chat_memory_conversation_id", chatId + INTENT_MEMORY_SUFFIX));
                    call = applyIntentOptions(call, intentCfg);
                    return call.call().content();
                }, "Phase1-intent");
            } catch (Exception e) {
                log.error("Phase 1 turbo调用失败: {}", e.getMessage());
                return "抱歉，AI服务暂时不可用，请稍后再试。";
            }

            boolean toolCalled = KnowledgeTools.wasToolCalled() || DateTimeTools.wasToolCalled();
            log.info("Phase 1 完成: toolCalled={}", toolCalled);

            // 空值兜底：Phase2 模型 content() 可能返回 null，统一转空串，避免下游 NPE/脏数据
            String answer;
            if (!toolCalled) {
                answer = polishWithPlus(question, turboOutput, chatId);
            } else {
                answer = generateWithRag(question, chatId, resolveRagContext(turboOutput));
            }
            return answer != null ? answer : "";
        } finally {
            // 清理 ThreadLocal，避免线程池复用导致工具调用标记/检索上下文泄漏到下一次请求
            KnowledgeTools.resetToolCalled();
            DateTimeTools.resetToolCalled();
        }
    }

    /**
     * 带重试的模型调用：外部模型端点（qwen3.8-max）偶发抖动/限流，
     * 瞬时失败自动重试，避免用户直接看到"AI服务暂时不可用"。
     * @param supplier 每次重试都要重新执行的模型调用（Lambda 内重建 ChatClient 请求）
     * @param stage     调用阶段名，用于日志
     */
    private String retry(Callable<String> supplier, String stage) {
        int maxAttempts = 3;
        for (int i = 1; i <= maxAttempts; i++) {
            try {
                return supplier.call();
            } catch (Exception e) {
                log.warn("{} 第{}次调用失败: {}", stage, i, e.getMessage());
                if (i >= maxAttempts) {
                    throw new RuntimeException(stage + " 调用失败", e);
                }
                try {
                    Thread.sleep(400L * i);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(stage + " 调用被中断", ie);
                }
            }
        }
        throw new IllegalStateException(stage + " 调用失败");
    }

    public Flux<String> chatStream(String question, String chatId) {
        log.info("ReAct 流式聊天: chatId={}, question={}", chatId,
                question.substring(0, Math.min(30, question.length())));

        // 提示词取用：「对话模型选择」中 intent 场景绑定的指令优先，未绑定才用 Prompt 设置的场景默认
        PromptConfig intentCfg = resolveScenePrompt("intent");
        String intentPrompt = getIntentPrompt(intentCfg);
        applyDefaultKbIfAbsent();
        KnowledgeTools.resetToolCalled();
        DateTimeTools.resetToolCalled();

        try {
            String turboOutput;
            try {
                turboOutput = retry(() -> {
                    var call = intentClient.prompt()
                            .system(intentPrompt)
                            .user(question)
                            .advisors(a -> a.param("chat_memory_conversation_id", chatId + INTENT_MEMORY_SUFFIX));
                    call = applyIntentOptions(call, intentCfg);
                    return call.call().content();
                }, "Phase1-intent-stream");
            } catch (Exception e) {
                log.error("Phase 1 turbo流式调用失败: {}", e.getMessage());
                return Flux.just("抱歉，AI服务暂时不可用，请稍后再试。");
            }

            boolean toolCalled = KnowledgeTools.wasToolCalled() || DateTimeTools.wasToolCalled();
            log.info("Phase 1 流式完成: toolCalled={}", toolCalled);

            if (toolCalled) {
                PromptConfig ragCfg = resolveScenePrompt("rag");
                // RAG 上下文放 system（不会写入记忆），user 只放干净问题（会写入记忆），保证多轮上下文不被参考资料污染
                return applyAnswerOptions(answerClient.prompt()
                        .system(buildRagSystemPrompt(ragCfg, resolveRagContext(turboOutput)))
                        .user(question)
                        .advisors(a -> a.param("chat_memory_conversation_id", chatId)), ragCfg, "rag")
                        .stream()
                        .content();
            }

            PromptConfig chatCfg = resolveScenePrompt("chat");
            return applyAnswerOptions(answerClient.prompt()
                    .system(getChatPrompt(chatCfg))
                    .user(question)
                    .advisors(a -> a.param("chat_memory_conversation_id", chatId)), chatCfg, "chat")
                    .stream()
                    .content();
        } finally {
            // 清理 ThreadLocal（Phase1 与 Flux 构建都在请求线程同步完成，此处清理安全）
            KnowledgeTools.resetToolCalled();
            DateTimeTools.resetToolCalled();
        }
    }

    private String polishWithPlus(String question, String turboOutput, String chatId) {
        PromptConfig chatCfg = resolveScenePrompt("chat");
        try {
            return retry(() -> {
                var call = answerClient.prompt()
                        .system(getChatPrompt(chatCfg))
                        .user(question)
                        .advisors(a -> a.param("chat_memory_conversation_id", chatId));
                call = applyAnswerOptions(call, chatCfg, "chat");
                return call.call().content();
            }, "Phase2-chat");
        } catch (Exception e) {
            log.warn("Phase 2 plus润色失败，使用turbo原始回答: {}", e.getMessage());
            return turboOutput;
        }
    }

    private String generateWithRag(String question, String chatId, String context) {
        PromptConfig ragCfg = resolveScenePrompt("rag");
        try {
            // 上下文放 system（不写入记忆），user 只放干净问题（写入记忆），conversationId 指向会话记忆
            return retry(() -> {
                var call = answerClient.prompt()
                        .system(buildRagSystemPrompt(ragCfg, context))
                        .user(question)
                        .advisors(a -> a.param("chat_memory_conversation_id", chatId));
                call = applyAnswerOptions(call, ragCfg, "rag");
                return call.call().content();
            }, "Phase2-rag");
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

    /**
     * 取某个对话场景最终使用的提示词指令。
     *
     * <p>优先级：「对话模型选择」中该场景绑定的指令 → Prompt 设置里该场景的默认指令。
     * 绑定层是"覆盖"，Prompt 设置是"默认值"。
     * <p>修复断链：绑定表里的 prompt_key 此前只存不读，管理员在「对话模型选择」里换了指令，
     * 运行时实际跑的还是 Prompt 设置的场景默认值 —— 页面上改了不生效。
     */
    private PromptConfig resolveScenePrompt(String scene) {
        try {
            ChatModelBinding binding = chatModelBindingService.getByScene(scene);
            if (binding != null && binding.getEnabled() != null && binding.getEnabled() == 1
                    && binding.getPromptKey() != null && !binding.getPromptKey().isBlank()) {
                PromptConfig bound = promptService.getConfigByKey(binding.getPromptKey());
                if (bound != null) {
                    return bound;
                }
                // 绑定指向的指令已删除/停用：回退场景默认值，并在日志里留痕，避免"静默换了提示词"
                log.warn("绑定指令不可用(已删除/停用/是关键词表)，回退场景默认指令: scene={}, promptKey={}",
                        scene, binding.getPromptKey());
            }
        } catch (Exception e) {
            log.warn("读取场景指令绑定失败(回退场景默认指令): scene={}, error={}", scene, e.getMessage());
        }
        return promptService.getConfigByScene(scene);
    }

    /**
     * 解析场景要使用的模型：
     * 优先级「对话模型选择(ai_chat_model_binding) → Prompt配置(prompt_config.model) → 常量兜底」。
     * 修复断链：管理端配置的模型此前从不参与运行时，这里真正生效。
     */
    private String resolveSceneModel(String scene, PromptConfig cfg, String fallbackModel) {
        try {
            ChatModelBinding binding = chatModelBindingService.getByScene(scene);
            if (binding != null && binding.getEnabled() != null && binding.getEnabled() == 1
                    && binding.getModelName() != null && !binding.getModelName().isBlank()) {
                return binding.getModelName();
            }
        } catch (Exception e) {
            log.warn("读取场景模型绑定失败(回退Prompt配置): scene={}, error={}", scene, e.getMessage());
        }
        if (cfg != null && cfg.getModel() != null && !cfg.getModel().isEmpty()) {
            return cfg.getModel();
        }
        return fallbackModel;
    }

    private ChatClient.ChatClientRequestSpec applyIntentOptions(
            ChatClient.ChatClientRequestSpec spec, PromptConfig cfg) {
        String model = resolveSceneModel("intent", cfg, DEFAULT_INTENT_MODEL);
        Double temp = cfg != null && cfg.getTemperature() != null
                ? cfg.getTemperature() : DEFAULT_INTENT_TEMP;
        // 意图路由只需短输出（调不调工具/一句话结论），限制 maxTokens 显著降低
        // Phase1 往返时间——它是流式回答前的同步阻塞段，直接决定首字延迟
        return spec.options(OpenAiChatOptions.builder()
                .model(model)
                .temperature(temp)
                .maxTokens(256)
                .build());
    }

    private ChatClient.ChatClientRequestSpec applyAnswerOptions(
            ChatClient.ChatClientRequestSpec spec, PromptConfig cfg, String scene) {
        String model = resolveSceneModel(scene, cfg, DEFAULT_ANSWER_MODEL);
        Double temp = cfg != null && cfg.getTemperature() != null
                ? cfg.getTemperature() : DEFAULT_ANSWER_TEMP;
        return spec.options(OpenAiChatOptions.builder()
                .model(model)
                .temperature(temp)
                .build());
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