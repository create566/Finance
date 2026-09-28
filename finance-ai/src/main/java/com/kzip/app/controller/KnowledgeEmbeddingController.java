package com.kzip.app.controller;

import com.kzip.app.service.KnowledgeEmbeddingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/ai/knowledge/embedding")
public class KnowledgeEmbeddingController {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeEmbeddingController.class);

    @Autowired
    private KnowledgeEmbeddingService knowledgeEmbeddingService;

    @PostMapping("/chunkCount")
    public Map<String, Object> calculateChunkCount(@RequestBody Map<String, String> params) {
        String content = params.get("content");
        String source = params.get("source");
        int chunkCount = knowledgeEmbeddingService.generateChunkCount(content, source);

        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "计算成功");
        result.put("data", Map.of("chunkCount", chunkCount));

        log.info("计算分块数: contentLength={}, source={}, chunkCount={}",
                 content != null ? content.length() : 0, source, chunkCount);

        return result;
    }

    @PostMapping("/process")
    public Map<String, Object> processDocument(@RequestBody Map<String, Object> params) {
        Object docIdObj = params.get("docId");
        Object kbIdObj = params.get("knowledgeBaseId");
        String content = (String) params.get("content");

        if (docIdObj == null || kbIdObj == null || content == null || content.isEmpty()) {
            Map<String, Object> result = new HashMap<>();
            result.put("code", 400);
            result.put("message", "参数不完整：docId、knowledgeBaseId、content 不能为空");
            return result;
        }

        Long docId = Long.valueOf(docIdObj.toString());
        Long knowledgeBaseId = Long.valueOf(kbIdObj.toString());

        knowledgeEmbeddingService.embedDocumentAsync(docId, content, knowledgeBaseId);

        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "向量化任务已提交，正在后台处理");
        result.put("data", Map.of(
                "docId", docId,
                "status", "processing",
                "message", "文档正在向量化中，请稍后查询状态"
        ));

        log.info("向量化任务已提交: docId={}, knowledgeBaseId={}", docId, knowledgeBaseId);

        return result;
    }
}