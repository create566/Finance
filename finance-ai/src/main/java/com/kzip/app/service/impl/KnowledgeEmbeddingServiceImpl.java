package com.kzip.app.service.impl;

import com.kzip.app.entity.KnowledgeDoc;
import com.kzip.app.service.KnowledgeBaseService;
import com.kzip.app.service.KnowledgeDocService;
import com.kzip.app.service.KnowledgeEmbeddingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
public class KnowledgeEmbeddingServiceImpl implements KnowledgeEmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeEmbeddingServiceImpl.class);

    @Autowired
    private VectorStore vectorStore;

    @Autowired
    private TokenTextSplitter textSplitter;

    @Autowired
    private KnowledgeDocService knowledgeDocService;

    @Autowired
    private KnowledgeBaseService knowledgeBaseService;

    @Override
    public int generateChunkCount(String content, String source) {
        if (content == null || content.isEmpty()) {
            return 0;
        }
        try {
            List<Document> chunks = textSplitter.split(List.of(new Document(content)));
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
            log.info("[{}] 开始向量化: docId={}, kbId={}, 内容长度={}",
                     taskId, docId, knowledgeBaseId, content.length());

            Document document = new Document(content, Map.of(
                    "docId", String.valueOf(docId),
                    "knowledgeBaseId", String.valueOf(knowledgeBaseId),
                    "source", "knowledge_base",
                    "taskId", taskId,
                    "startTime", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            ));

            log.info("[{}] 正在分割文本...", taskId);
            long t1 = System.currentTimeMillis();
            List<Document> chunks = textSplitter.split(List.of(document));
            log.info("[{}] 分割完成: {}个分块, 耗时{}ms",
                     taskId, chunks.size(), System.currentTimeMillis() - t1);

            // 关键修复：先按 docId 删除该文档的历史向量，再写入新向量。
            // 否则重复上传/重新向量化会在 ChromaDB 累积重复切片，导致 RAG 命中重复片段。
            try {
                FilterExpressionBuilder b = new FilterExpressionBuilder();
                vectorStore.delete(b.eq("docId", String.valueOf(docId)).build());
                log.info("[{}] 已清理 docId={} 的历史向量", taskId, docId);
            } catch (Exception e) {
                // 若旧向量删除失败，仍继续写入，交由上层记录（最坏是存在少量重复）
                log.warn("[{}] 清理历史向量失败(继续写入): docId={}, error={}",
                        taskId, docId, e.getMessage());
            }

            // 内容去重：为每个分块计算内容指纹(contentHash)写入 metadata，
            // 写入前按指纹清理向量库中已存在的同内容向量，拒绝跨文档内容重复。
            // 相同文本片段即使不同文档/不同批次再次命中，也不会在 ChromaDB 中累积多份。
            for (Document chunk : chunks) {
                chunk.getMetadata().put("contentHash", md5(chunk.getText()));
            }
            FilterExpressionBuilder fb = new FilterExpressionBuilder();
            for (Document chunk : chunks) {
                try {
                    vectorStore.delete(fb.eq("contentHash",
                            String.valueOf(chunk.getMetadata().get("contentHash"))).build());
                } catch (Exception e) {
                    log.debug("[{}] 清理同内容向量失败(忽略): {}", taskId, e.getMessage());
                }
            }

            log.info("[{}] 正在生成向量并写入ChromaDB...", taskId);
            long t2 = System.currentTimeMillis();
            vectorStore.add(chunks);
            log.info("[{}] ChromaDB写入完成: {}个向量, 耗时{}ms",
                     taskId, chunks.size(), System.currentTimeMillis() - t2);

            KnowledgeDoc doc = knowledgeDocService.getById(docId);
            if (doc != null) {
                int oldChunkCount = doc.getChunkCount() != null ? doc.getChunkCount() : 0;
                doc.setStatus(1);
                doc.setChunkCount(chunks.size());
                knowledgeDocService.updateById(doc);

                int delta = chunks.size() - oldChunkCount;
                if (delta > 0) {
                    knowledgeBaseService.addChunkCount(knowledgeBaseId, delta);
                }
                log.info("[{}] ✅ 向量化成功, 状态已更新为就绪: docId={}, chunkCount={}, kbChunkDelta={}",
                         taskId, docId, chunks.size(), delta);
            } else {
                log.warn("[{}] 文档不存在无法更新状态: docId={}", taskId, docId);
            }

            long total = System.currentTimeMillis() - startTime;
            log.info("[{}] ✅ 任务完成: docId={}, 总耗时={}ms", taskId, docId, total);

        } catch (Exception e) {
            long total = System.currentTimeMillis() - startTime;
            log.error("[{}] ❌ 向量化失败: docId={}, 耗时={}ms, error={}",
                      taskId, docId, total, e.getMessage(), e);

            KnowledgeDoc doc = knowledgeDocService.getById(docId);
            if (doc != null) {
                doc.setStatus(2);
                knowledgeDocService.updateById(doc);
            }
        }
    }

    @Override
    public void deleteDocumentVectors(Long docId) {
        try {
            FilterExpressionBuilder b = new FilterExpressionBuilder();
            vectorStore.delete(b.eq("docId", String.valueOf(docId)).build());
            log.info("已删除 docId={} 的向量", docId);
        } catch (Exception e) {
            log.warn("删除 docId={} 向量失败: {}", docId, e.getMessage());
        }
    }

    /** 内容指纹：MD5 十六进制，用于向量去重（拒绝同内容重复入库） */
    private String md5(String text) {
        try {
            java.security.MessageDigest md =
                    java.security.MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            // 极端情况回退到内容本身，保证去重键唯一
            return Integer.toHexString(text.hashCode());
        }
    }
}