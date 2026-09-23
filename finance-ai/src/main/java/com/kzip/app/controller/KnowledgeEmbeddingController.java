package com.kzip.app.controller;

import com.kzip.app.service.KnowledgeEmbeddingService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Api(tags = "知识库向量嵌入")
@RestController
@RequestMapping("/ai/knowledge/embedding")
@Slf4j
public class KnowledgeEmbeddingController {

    @Autowired
    private KnowledgeEmbeddingService knowledgeEmbeddingService;

    @ApiOperation("计算分块数量")
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

    @ApiOperation("触发文档向量化")
    @PostMapping("/process")
    public Map<String, Object> processDocument(@RequestBody Map<String, Object> params) {
        Long docId = Long.valueOf(params.get("docId").toString());
        String content = (String) params.get("content");
        Long knowledgeBaseId = Long.valueOf(params.get("knowledgeBaseId").toString());

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