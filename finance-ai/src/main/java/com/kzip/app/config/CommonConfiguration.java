package com.kzip.app.config;

import org.springframework.ai.openai.OpenAiChatOptions;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kzip.app.tool.DateTimeTools;
import com.kzip.app.tool.KnowledgeTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import com.kzip.app.memory.DbChatMemoryRepository;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chroma.vectorstore.ChromaApi;
import org.springframework.ai.chroma.vectorstore.ChromaVectorStore;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;



@Configuration
public class CommonConfiguration {

    @Value("${spring.ai.chroma.client.host:127.0.0.1}")
    private String chromaHost;

    @Value("${spring.ai.chroma.client.port:8000}")
    private int chromaPort;

    @Bean
    public ChromaApi chromaApi(ObjectMapper objectMapper) {
        String baseUrl = "http://" + chromaHost + ":" + chromaPort;
        // 强制 RestClient 使用 UTF-8 编码，避免中文被默认 ISO-8859-1 编码后写入 ChromaDB 产生乱码
        RestClient.Builder restClientBuilder = RestClient.builder()
                .messageConverters(converters -> {
                    converters.removeIf(c -> c instanceof StringHttpMessageConverter);
                    converters.add(0, new StringHttpMessageConverter(StandardCharsets.UTF_8));
                });
        return new ChromaApi(baseUrl, restClientBuilder, objectMapper);
    }

    @Bean
    public VectorStore vectorStore(ChromaApi chromaApi, EmbeddingModel embeddingModel) {
        try {
            chromaApi.createDatabase("SpringAiTenant", "SpringAiDatabase");
        } catch (Exception e) {
        }
        try {
            chromaApi.createCollection("SpringAiTenant", "SpringAiDatabase",
                    new ChromaApi.CreateCollectionRequest("pdf_rag_collection"));
        } catch (Exception e) {
        }
        return ChromaVectorStore.builder(chromaApi, embeddingModel)
                .collectionName("pdf_rag_collection")
                .initializeSchema(true)
                .build();
    }

    @Bean
    public TokenTextSplitter tokenTextSplitter() {
        return new TokenTextSplitter(500, 50, 1000, 10000, false);
    }

    @Bean
    public ChatMemory chatMemory(DbChatMemoryRepository chatMemoryRepository) {
        // 使用数据库持久化仓库替代 InMemoryChatMemoryRepository，
        // 使 MessageChatMemoryAdvisor 的上下文记忆在服务重启后依然可读。
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(20)
                .build();
    }

    /**
     * 意图路由 ChatClient（Phase 1）
     * 模型：qwen3.8-max（意图快判）
     * 职责：判断意图 + 执行工具（searchKnowledgeBase / getCurrentDateTime）
     */
    @Bean
    public ChatClient intentClient(ChatModel model, ChatMemory chatMemory,
                                   KnowledgeTools knowledgeTools) {
        return ChatClient.builder(model)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model("qwen3.8-max")
                        .temperature(0.0)
                        .build())
                .defaultTools(new DateTimeTools(), knowledgeTools)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        new SimpleLoggerAdvisor()
                )
                .build();
    }

    /**
     * 回答生成 ChatClient（Phase 2）
     * 模型：qwen3.8-max（回答生成）
     * 职责：基于上下文生成高质量最终回答
     */
    @Bean
    public ChatClient answerClient(ChatModel model, ChatMemory chatMemory) {
        return ChatClient.builder(model)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model("qwen3.8-max")
                        .temperature(0.3)
                        .build())
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        new SimpleLoggerAdvisor()
                )
                .build();
    }
}