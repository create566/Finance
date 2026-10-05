package com.kzip.app.tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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

    /**
     * 当前请求选中的知识库 id（前端知识库下拉选择，经 ChatController 传入）。
     * 此前检索从不按库过滤，所有知识库数据混在一个 collection 里互相污染，
     * 前端的选择器形同虚设；现在检索时按 metadata knowledgeBaseId 过滤。
     */
    private static final ThreadLocal<String> currentKbId = new ThreadLocal<>();

    /** 相似度阈值（similarity = 1 - distance）。实测数据：相关查询 distance 0.51~0.61，
     *  无关查询 0.67~0.76，故默认 0.35（即 distance 上限 0.65）可干净切分；
     *  可通过 rag.search.similarity-threshold 配置调整（设为 0 关闭过滤）。 */
    @Value("${rag.search.similarity-threshold:0.35}")
    private double similarityThreshold;

    @Autowired
    private VectorStore vectorStore;

    public static boolean wasToolCalled() {
        return toolCalled.get();
    }

    public static String getLastSearchContext() {
        return lastSearchContext.get();
    }

    public static void setCurrentKbId(Long kbId) {
        if (kbId != null && kbId > 0) {
            currentKbId.set(String.valueOf(kbId));
        } else {
            currentKbId.remove();
        }
    }

    /** 当前请求已选定的知识库 id（未选定时为 null），供"绑定兜底"判断是否需要用配置的默认库 */
    public static Long getCurrentKbId() {
        String id = currentKbId.get();
        if (id == null || id.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(id);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 清理"工具是否被调用"与检索上下文。
     * 注意：不清 currentKbId —— 它是"本轮请求"的入参上下文，
     * 而 ReActService 在每个阶段开始都会调用本方法，若在此清 kbId
     * 会把 Controller 刚设置的检索范围清掉，导致知识库过滤失效。
     * kbId 由 Controller 在请求结束时调用 clearKbId() 清理。
     */
    public static void resetToolCalled() {
        toolCalled.remove();
        lastSearchContext.remove();
    }

    /** 请求结束时清理知识库检索范围（ThreadLocal 防串号） */
    public static void clearKbId() {
        currentKbId.remove();
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
        log.info("vectorStore 实现类={}, 集合名=pdf_rag_collection", vectorStore.getClass().getName());
        log.info("知识库检索工具被调用: query={}", query);
        try {
            // 注意：Spring AI 1.1.2 的 similarityThreshold 要求阈值落在 [0,1] 区间，
            // 传 -1.0 会抛 IllegalArgumentException。阈值取配置项（默认 0.35），
            // 设为 0 可关闭过滤。
            SearchRequest.Builder builder = SearchRequest.builder()
                    .query(query)
                    .topK(6);
            if (similarityThreshold > 0) {
                builder.similarityThreshold(similarityThreshold);
            }
            String kbId = currentKbId.get();
            if (kbId != null) {
                // 按当前会话选中的知识库过滤，避免多库数据互相污染
                builder.filterExpression(new FilterExpressionBuilder()
                        .eq("knowledgeBaseId", kbId).build());
                log.info("知识库检索限定 kbId={}", kbId);
            }
            List<Document> docs = vectorStore.similaritySearch(builder.build());
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
            log.error("知识库检索失败", e);
            lastSearchContext.remove();
            return "知识库检索暂时不可用，请稍后再试";
        }
    }
}