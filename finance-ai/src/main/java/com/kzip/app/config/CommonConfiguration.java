package com.kzip.app.config;


import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CommonConfiguration {

    @Bean
    public VectorStore vectorStore(EmbeddingModel embeddingModel) {
        return SimpleVectorStore.builder(embeddingModel).build();
    }



    // ======================================================================================
    //  【场景 1：同一厂商切换不同子模型】—— 99% 的项目只需要这个
    //
    //  当前：DashScope 阿里云
    //  子模型举例：
    //    qwen-turbo       便宜、快、日常闲聊
    //    qwen-plus        推理更强、RAG 问答首选
    //    qwen-max         最聪明、最贵、长/复杂任务
    //    qwen-long        超长上下文（百万 tokens）
    //
    //  三种切换粒度，按需选：
    //    ① 【Bean 级别】不同 ChatClient Bean 各自绑定固定模型（例如 chatClientTurbo / chatClientRag）
    //    ② 【调用级别】调用时用 .options() 单次覆盖（一行就能切，最灵活）
    //    ③ 【用户传入】Controller 接口接 model 参数动态传（完全由前端决定用哪个）
    // ======================================================================================

    // ---- ① Bean 级别：绑定 qwen-turbo（快、便宜，默认普通聊天） ----
    @Bean
    public ChatClient chatClient(ChatModel model, ChatMemory chatMemory) {
        return ChatClient.builder(model)
                .defaultOptions(DashScopeChatOptions.builder()
                        .withModel("qwen-turbo")
                        .withTemperature(0.7)
                        .build())
                .defaultAdvisors(
                        // ⚠️ Spring AI 1.1.x 里 MessageChatMemoryAdvisor 构造器是 private，
                        //    必须使用公开的 builder() 静态方法，不能直接 new。
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        new SimpleLoggerAdvisor()
                )
                .build();
    }

    // ---- ① Bean 级别：绑定 qwen-plus（RAG/知识问答用） ----
    @Bean
    public ChatClient pdfChatClient(
            ChatModel model,
            ChatMemory chatMemory,
            VectorStore vectorStore) {

        // 运行时诊断：启动时打印到底注入了哪家厂商的 ChatModel 实现
        System.out.println("【pdfChatClient 注入的 ChatModel】实现类 = " + model.getClass().getName());

        return ChatClient.builder(model)
                .defaultOptions(DashScopeChatOptions.builder()
                        .withModel("qwen-plus")
                        .withTemperature(0.2)   // 问答场景温度低一点，回答稳定
                        .build())
                .defaultSystem("请根据提供的上下文回答问题，不要自己猜测。")
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        new SimpleLoggerAdvisor(),
                        QuestionAnswerAdvisor.builder(vectorStore)
                                .searchRequest(SearchRequest.builder()
                                        .similarityThreshold(0.5d)
                                        .topK(2)
                                        .build())
                                .build()
                )
                .build();
    }

    // ---- ② 调用级别切换示例（写在 Controller 里都行，与 @Bean 无关）----
    //  String answer = chatClient.prompt()
    //          .user(userInput)
    //          .options(DashScopeChatOptions.builder().withModel("qwen-max").build())   // 这次强制用 qwen-max
    //          .call()
    //          .content();

    // ---- ③ 用户动态指定模型的 Controller 写法示意（无需改本配置类）----
    //  @GetMapping("/chat/{model}")
    //  public String chat(@PathVariable String model, @RequestParam String userInput) {
    //      return chatClient.prompt()
    //              .user(userInput)
    //              .options(DashScopeChatOptions.builder().withModel(model).build())   // qwen-turbo/qwen-plus/qwen-max 任意
    //              .call()
    //              .content();
    //  }

    // ======================================================================================
    //  【场景 2：多厂商并存（DashScope + OpenAI + Ollama + ...）】—— 除非明确做"模型厂切换产品"，一般不用
    //
    //  前提：pom 同时引入多个 starter
    //      - spring-ai-alibaba-starter-dashscope
    //      - spring-ai-openai-spring-boot-starter
    //      - spring-ai-ollama-spring-boot-starter
    //
    //  风险：两套自动装配都会各自注册 ChatModel / EmbeddingModel Bean，
    //        直接 @Autowired ChatModel 会抛 "required a single bean, but 3 were found"
    //  解决方案（二选一）：
    //
    //  ▶ 方案 2.1：标记其中一个为默认（@Primary），其它注入点用 @Qualifier("xxx")
    //      @Bean
    //      @Primary
    //      public ChatModel dashscopeChatModel(DashScopeApi dashScopeApi) {
    //          return new DashScopeChatModel(dashScopeApi, DashScopeChatOptions.builder().withModel("qwen-plus").build());
    //      }
    //      // 注入处：
    //      //   @Autowired ChatModel defaultModel;                     → 拿到 DashScope @Primary 的
    //      //   @Autowired @Qualifier("openAiChatModel") ChatModel gpt; → 拿到 OpenAI 的
    //
    //  ▶ 方案 2.2：彻底不走自动装配，在配置类里手动 new 全部 Model（最可控，推荐）
    //      需要在 @SpringBootApplication 上 exclude 掉对应 AutoConfiguration，
    //      或者配置里写 spring.ai.dashscope.chat.enabled=false / spring.ai.openai.chat.enabled=false
    //      先禁用自动注册，然后手动 @Bean 返回 DashScopeChatModel / OpenAiChatModel / OllamaChatModel。
    //
    //  当前你这个 Demo 项目单厂商足够，场景 2 的代码我就不写到 Bean 里了，避免引入不必要的依赖。
    // ======================================================================================

    @Bean
    public ChatMemory chatMemory() {
        // Spring AI 1.1.x 架构：ChatMemory 接口的实现改为 MessageWindowChatMemory，
        // 底层存储由 ChatMemoryRepository（这里用 InMemoryChatMemoryRepository，基于 ConcurrentHashMap）负责。
        // 旧版的 InMemoryChatMemory 类已废弃删除。
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(new InMemoryChatMemoryRepository())
                .maxMessages(20)
                .build();
    }
}
