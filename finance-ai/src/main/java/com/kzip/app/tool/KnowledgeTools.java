package com.kzip.app.tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class KnowledgeTools {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeTools.class);

    private static final ThreadLocal<Boolean> toolCalled = ThreadLocal.withInitial(() -> false);

    /**
     * 记录最近一次知识库检索的原文片段，供 Phase 2 生成时直接引用，
     * 避免依赖模型对工具结果的复述导致 RAG 语义失真。
     * 注意：Phase 1 为同步调用，工具执行与读取在同一线程，ThreadLocal 可靠；
     * 若未来改为异步/流式工具执行，需改用显式上下文传递。
     */
    private static final ThreadLocal<String> lastSearchContext = new ThreadLocal<>();

    @Autowired
    private VectorStore vectorStore;

    public static boolean wasToolCalled() {
        return toolCalled.get();
    }

    public static String getLastSearchContext() {
        return lastSearchContext.get();
    }

    public static void resetToolCalled() {
        toolCalled.remove();
        lastSearchContext.remove();
    }

    @Tool(name = "searchKnowledgeBase", description = """
            搜索金融知识库，获取与用户问题相关的专业知识。
            当用户询问以下类型问题时必须调用此工具：
            - 借款利率、费用、还款方式、贷款政策
            - 业务流程、审核标准、申请条件
            - 金融产品说明、合同条款
            - 任何需要专业知识才能回答的问题
            参数 query: 用简洁的关键词描述用户想了解的内容
            """)
    public String searchKnowledgeBase(String query) {
        toolCalled.set(true);
        log.info("知识库检索工具被调用: query={}", query);
        try {
            List<Document> docs = vectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query(query)
                            .topK(6)
                            .similarityThreshold(-1.0)
                            .build()
            );
            if (docs.isEmpty()) {
                lastSearchContext.remove();
                log.warn("知识库检索返回空: query={}, ChromaDB中可能无数据或维度不匹配", query);
                return "知识库中暂无与「" + query + "」直接相关的资料";
            }
            // 打印每条命中文档的 score，便于观察实际相似度分布、后续调优阈值
            docs.forEach(d -> log.info("  命中片段: score={}, textLen={}, preview={}",
                    d.getMetadata().get("distance"),
                    d.getText().length(),
                    d.getText().substring(0, Math.min(50, d.getText().length())).replace("\n", " ")
            ));
            String result = docs.stream()
                    .map(Document::getText)
                    .collect(Collectors.joining("\n---\n"));
            lastSearchContext.set(result);
            log.info("知识库检索完成: 返回 {} 条文档片段", docs.size());
            return result;
        } catch (Exception e) {
            log.error("知识库检索失败: {}", e.getMessage());
            lastSearchContext.remove();
            return "知识库检索暂时不可用，请稍后再试";
        }
    }
}