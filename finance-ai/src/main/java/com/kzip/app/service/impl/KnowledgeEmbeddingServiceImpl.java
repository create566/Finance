package com.kzip.app.service.impl;

import com.kzip.app.service.KnowledgeEmbeddingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class KnowledgeEmbeddingServiceImpl implements KnowledgeEmbeddingService {

    @Autowired
    private VectorStore vectorStore;

    @Autowired
    private TokenTextSplitter textSplitter;

    @Autowired(required = false)
    private RestTemplate restTemplate;

    @Value("${api.module.callback-url:http://localhost:8990/admin/core/knowledge}")
    private String apiCallbackUrl;

    @Value("${api.module.health-url:http://localhost:8990/admin/core/knowledge/health}")
    private String apiHealthUrl;

    @Override
    public int generateChunkCount(String content, String source) {
        if (content == null || content.isEmpty()) {
            return 0;
        }

        try {
            List<String> chunks = textSplitter.split(List.of(new Document(content)));
            log.info("文档分块完成: 原始长度={}, 分块数={}", content.length(), chunks.size());
            return chunks.size();
        } catch (Exception e) {
            log.warn("分块失败，使用简单估算: {}", e.getMessage());
            if ("pdf".equalsIgnoreCase(source)) {
                return (int) Math.ceil((double) content.length() / 400);
            } else {
                return (int) Math.ceil((double) content.length() / 500);
            }
        }
    }

    @Override
    @Async("taskExecutor")
    public void embedDocumentAsync(Long docId, String content, Long knowledgeBaseId) {
        long startTime = System.currentTimeMillis();
        String taskId = "EMB_" + System.currentTimeMillis() + "_" + docId;

        try {
            log.info("[{}] 开始向量化处理: docId={}, knowledgeBaseId={}, 内容长度={}",
                     taskId, docId, knowledgeBaseId, content.length());

            checkApiModuleHealth(taskId);

            Document document = new Document(content, Map.of(
                    "docId", String.valueOf(docId),
                    "knowledgeBaseId", String.valueOf(knowledgeBaseId),
                    "source", "knowledge_base",
                    "taskId", taskId,
                    "startTime", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            ));

            log.info("[{}] 正在分割文本...", taskId);
            long splitStartTime = System.currentTimeMillis();
            List<Document> chunks = textSplitter.split(List.of(document));
            long splitEndTime = System.currentTimeMillis();
            log.info("[{}] 文本分割完成: 共{}个分块, 耗时{}ms",
                     taskId, chunks.size(), (splitEndTime - splitStartTime));

            log.info("[{}] 正在生成向量并存储到ChromaDB...", taskId);
            long embedStartTime = System.currentTimeMillis();
            vectorStore.add(chunks);
            long embedEndTime = System.currentTimeMillis();
            log.info("[{}] 向量存储成功: docId={}, chunkCount={}, 向量化耗时{}ms",
                     taskId, docId, chunks.size(), (embedEndTime - embedStartTime));

            notifyApiModuleSuccess(docId, chunks.size(), taskId);

            long totalTime = System.currentTimeMillis() - startTime;
            log.info("[{}] ✅ 向量化任务完成: docId={}, 总耗时={}ms, 分块数={}",
                     taskId, docId, totalTime, chunks.size());

        } catch (Exception e) {
            long totalTime = System.currentTimeMillis() - startTime;
            log.error("[{}] ❌ 文档向量化失败: docId={}, 总耗时={}ms, error={}",
                      taskId, docId, totalTime, e.getMessage(), e);
            notifyApiModuleFailure(docId, e.getMessage(), taskId);
            throw new RuntimeException("文档向量化失败: " + e.getMessage(), e);
        }
    }

    private void checkApiModuleHealth(String taskId) {
        if (restTemplate == null) {
            log.warn("[{}] RestTemplate未注入，跳过API健康检查", taskId);
            return;
        }

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> healthResponse = restTemplate.getForObject(apiHealthUrl, Map.class);
            if (healthResponse != null && "UP".equals(healthResponse.get("status"))) {
                log.info("[{}] API模块健康检查通过: {}", taskId, healthResponse);
            } else {
                log.warn("[{}] API模块状态异常: {}", taskId, healthResponse);
            }
        } catch (Exception e) {
            log.warn("[{}] API模块健康检查失败（继续执行）: error={}", taskId, e.getMessage());
        }
    }

    private void notifyApiModuleSuccess(Long docId, int chunkCount, String taskId) {
        if (restTemplate == null) {
            log.warn("[{}] RestTemplate未注入，无法通知API模块更新状态: docId={}", taskId, docId);
            return;
        }

        try {
            String callbackUrl = apiCallbackUrl + "/doc/" + docId + "/embedding-status";

            Map<String, Object> statusInfo = new HashMap<>();
            statusInfo.put("status", 1);
            statusInfo.put("chunkCount", chunkCount);
            statusInfo.put("message", "向量化成功完成");
            statusInfo.put("taskId", taskId);
            statusInfo.put("completedTime", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(callbackUrl, statusInfo, Map.class);

            log.info("[{}] ✅ 已成功通知API模块: docId={}, 状态=就绪, 分块数={}, 响应={}",
                     taskId, docId, chunkCount, response);
        } catch (Exception e) {
            log.error("[{}] ❌ 通知API模块失败: docId={}, error={}", taskId, docId, e.getMessage(), e);
        }
    }

    private void notifyApiModuleFailure(Long docId, String errorMessage, String taskId) {
        if (restTemplate == null) {
            log.warn("[{}] RestTemplate无法通知API模块失败状态: docId={}", taskId, docId);
            return;
        }

        try {
            String callbackUrl = apiCallbackUrl + "/doc/" + docId + "/embedding-status";

            Map<String, Object> statusInfo = new HashMap<>();
            statusInfo.put("status", 2);
            statusInfo.put("message", "向量化失败: " + errorMessage);
            statusInfo.put("taskId", taskId);
            statusInfo.put("failedTime", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

            restTemplate.postForObject(callbackUrl, statusInfo, Map.class);

            log.info("[{}] 已通知API模块向量化失败: docId={}, error={}", taskId, docId, errorMessage);
        } catch (Exception ex) {
            log.error("[{}] 通知API模块失败状态也失败了: docId={}, error={}", taskId, docId, ex.getMessage());
        }
    }
}