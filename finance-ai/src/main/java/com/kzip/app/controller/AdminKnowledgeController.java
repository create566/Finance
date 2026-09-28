package com.kzip.app.controller;

import com.kzip.app.entity.KnowledgeBase;
import com.kzip.app.entity.KnowledgeDoc;
import com.kzip.app.repository.FileRepository;
import com.kzip.app.service.KnowledgeBaseService;
import com.kzip.app.service.KnowledgeDocService;
import com.kzip.app.service.KnowledgeEmbeddingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/ai/admin/knowledge")
public class AdminKnowledgeController {

    private static final Logger log = LoggerFactory.getLogger(AdminKnowledgeController.class);

    @Autowired
    private KnowledgeBaseService knowledgeBaseService;

    @Autowired
    private KnowledgeDocService knowledgeDocService;

    @Autowired
    private KnowledgeEmbeddingService knowledgeEmbeddingService;

    @Autowired
    private FileRepository fileRepository;

    @GetMapping("/base/list")
    public Result<List<KnowledgeBase>> baseList(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status) {
        return Result.success("查询成功", knowledgeBaseService.listAll(keyword, status));
    }

    @GetMapping("/base/{id}")
    public Result<KnowledgeBase> baseGet(@PathVariable Long id) {
        KnowledgeBase kb = knowledgeBaseService.getById(id);
        if (kb == null) {
            return Result.error(404, "知识库不存在");
        }
        return Result.success("查询成功", kb);
    }

    @PostMapping("/base")
    public Result<Long> baseAdd(@RequestBody KnowledgeBase base) {
        boolean ok = knowledgeBaseService.saveBase(base);
        return ok ? Result.success("新增成功", base.getId())
                : Result.error(500, "新增失败");
    }

    @PutMapping("/base")
    public Result<Object> baseUpdate(@RequestBody KnowledgeBase base) {
        boolean ok = knowledgeBaseService.updateBase(base);
        return ok ? Result.success("修改成功", null)
                : Result.error(500, "修改失败");
    }

    @DeleteMapping("/base/{id}")
    public Result<Object> baseDelete(@PathVariable Long id) {
        List<KnowledgeDoc> docs = knowledgeDocService.listByKbId(id);
        for (KnowledgeDoc doc : docs) {
            knowledgeDocService.removeDoc(doc.getId());
        }
        boolean ok = knowledgeBaseService.removeBase(id);
        return ok ? Result.success("删除成功", null)
                : Result.error(500, "删除失败");
    }

    @GetMapping("/doc/list")
    public Result<List<KnowledgeDoc>> docList(@RequestParam Long kbId) {
        return Result.success("查询成功", knowledgeDocService.listByKbId(kbId));
    }

    @GetMapping("/doc/{id}")
    public Result<KnowledgeDoc> docGet(@PathVariable Long id) {
        KnowledgeDoc doc = knowledgeDocService.getById(id);
        if (doc == null) {
            return Result.error(404, "文档不存在");
        }
        return Result.success("查询成功", doc);
    }

    @PostMapping("/doc/text")
    public Result<Long> docAddText(@RequestBody Map<String, Object> params) {
        Long kbId = Long.valueOf(params.get("kbId").toString());
        String title = (String) params.get("title");
        String content = (String) params.get("content");
        if (title == null || title.trim().isEmpty()) {
            return Result.error(400, "标题不能为空");
        }
        if (content == null || content.trim().isEmpty()) {
            return Result.error(400, "内容不能为空");
        }
        KnowledgeDoc doc = knowledgeDocService.addTextDoc(kbId, title, content);
        return doc.getId() != null ? Result.success("添加成功", doc.getId())
                : Result.error(500, "添加失败");
    }

    @PostMapping("/doc/upload")
    public Result<Long> docUpload(
            @RequestParam Long kbId,
            @RequestParam String title,
            @RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return Result.error(400, "文件不能为空");
        }
        String fileName = file.getOriginalFilename();
        KnowledgeDoc doc = knowledgeDocService.addPdfDoc(kbId, title, fileName, file.getSize());
        if (doc.getId() == null) {
            return Result.error(500, "上传失败");
        }

        try {
            byte[] fileBytes = file.getBytes();
            fileRepository.save(doc.getId().toString(), new ByteArrayResource(fileBytes));

            PdfDocumentReaderConfig config = PdfDocumentReaderConfig.builder()
                    .withPageTopMargin(0)
                    .withPageBottomMargin(0)
                    .build();
            PagePdfDocumentReader reader = new PagePdfDocumentReader(
                    new ByteArrayResource(fileBytes), config);
            String content = reader.get().stream()
                    .map(d -> d.getText())
                    .reduce("", (a, b) -> a + "\n" + b);

            doc.setContent(content);
            knowledgeDocService.updateById(doc);

            knowledgeEmbeddingService.embedDocumentAsync(doc.getId(), content, kbId);
            log.info("PDF上传成功，已触发异步向量化: docId={}, 内容长度={}", doc.getId(), content.length());
        } catch (Exception e) {
            log.error("PDF处理失败: docId={}, error={}", doc.getId(), e.getMessage(), e);
            doc.setStatus(2);
            knowledgeDocService.updateById(doc);
            return Result.success("文件已上传，但处理失败: " + e.getMessage(), doc.getId());
        }

        return Result.success("上传成功，等待处理", doc.getId());
    }

    @DeleteMapping("/doc/{id}")
    public Result<Object> docDelete(@PathVariable Long id) {
        boolean ok = knowledgeDocService.removeDoc(id);
        return ok ? Result.success("删除成功", null)
                : Result.error(500, "删除失败");
    }

    @PostMapping("/doc/{docId}/embedding-status")
    public Result<Map<String, Object>> updateEmbeddingStatus(@PathVariable Long docId,
                                                              @RequestBody Map<String, Object> statusInfo) {
        KnowledgeDoc doc = knowledgeDocService.getById(docId);
        if (doc == null) {
            return Result.error(404, "文档不存在");
        }
        Integer status = statusInfo.get("status") != null
                ? ((Number) statusInfo.get("status")).intValue() : null;
        Integer chunkCount = statusInfo.get("chunkCount") != null
                ? ((Number) statusInfo.get("chunkCount")).intValue() : null;

        if (status != null) {
            doc.setStatus(status);
        }
        if (chunkCount != null) {
            doc.setChunkCount(chunkCount);
        }
        knowledgeDocService.updateById(doc);
        log.info("向量化状态更新: docId={}, status={}, chunkCount={}", docId, status, chunkCount);

        Map<String, Object> data = new HashMap<>();
        data.put("docId", docId);
        data.put("status", status);
        data.put("chunkCount", chunkCount);
        return Result.success("文档状态已更新", data);
    }

    @GetMapping("/health")
    public Map<String, Object> healthCheck() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("service", "finance-ai");
        health.put("timestamp", System.currentTimeMillis());
        return health;
    }
}