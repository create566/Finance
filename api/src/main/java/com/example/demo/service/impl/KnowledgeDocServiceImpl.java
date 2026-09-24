package com.example.demo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.demo.entity.KnowledgeBase;
import com.example.demo.entity.KnowledgeDoc;
import com.example.demo.exception.BusinessException;
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.mapper.KnowledgeDocMapper;
import com.example.demo.service.KnowledgeBaseService;
import com.example.demo.service.KnowledgeDocService;
import com.example.demo.service.strategy.FileStorageStrategy;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class KnowledgeDocServiceImpl extends ServiceImpl<KnowledgeDocMapper, KnowledgeDoc> implements KnowledgeDocService {

    @Value("${ai.embedding.service-url:http://localhost:8081/ai/knowledge/embedding}")
    private String aiEmbeddingServiceUrl;

    @Autowired
    private KnowledgeBaseService knowledgeBaseService;

    @Autowired
    private FileStorageStrategy fileStorageStrategy;

    @Autowired(required = false)
    private RestTemplate restTemplate;

    @Override
    public IPage<KnowledgeDoc> pageList(Long kbId, Integer pageNum, Integer pageSize, String title, String source, Integer status) {
        LambdaQueryWrapper<KnowledgeDoc> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KnowledgeDoc::getKbId, kbId);
        if (title != null && !title.trim().isEmpty()) {
            wrapper.like(KnowledgeDoc::getTitle, title.trim());
        }
        if (source != null && !source.trim().isEmpty()) {
            wrapper.eq(KnowledgeDoc::getSource, source);
        }
        if (status != null) {
            wrapper.eq(KnowledgeDoc::getStatus, status);
        }
        wrapper.orderByDesc(KnowledgeDoc::getCreateTime);
        return this.page(new Page<>(pageNum, pageSize), wrapper);
    }

    @Override
    public KnowledgeDoc saveTextDoc(KnowledgeDoc doc) {
        if (doc.getKbId() == null) {
            throw new BusinessException(400, "知识库ID不能为空");
        }
        if (doc.getTitle() == null || doc.getTitle().trim().isEmpty()) {
            throw new BusinessException(400, "文档标题不能为空");
        }
        if (doc.getContent() == null || doc.getContent().trim().isEmpty()) {
            throw new BusinessException(400, "文档内容不能为空");
        }

        KnowledgeBase kb = knowledgeBaseService.getById(doc.getKbId());
        if (kb == null) {
            throw new ResourceNotFoundException(404, "知识库不存在");
        }

        int chunkCount = calculateChunkCountFromAI(doc.getContent(), "text");

        doc.setSource("text");
        doc.setFileName("");
        doc.setFileSize(0);
        doc.setFileUrl("");
        doc.setChunkCount(chunkCount);
        doc.setStatus(0);

        boolean ok = this.save(doc);
        if (!ok) {
            throw new BusinessException(500, "保存文档失败");
        }

        knowledgeBaseService.updateStatistics(doc.getKbId());
        triggerEmbeddingFromAI(doc.getId(), doc.getContent(), doc.getKbId());

        log.info("添加文本文档成功: id={}, title={}, chunkCount={}", doc.getId(), doc.getTitle(), chunkCount);
        return doc;
    }

    @Override
    public Map<String, Object> savePdfDoc(Long kbId, String title, MultipartFile file) {
        if (kbId == null) {
            throw new BusinessException(400, "知识库ID不能为空");
        }
        if (title == null || title.trim().isEmpty()) {
            throw new BusinessException(400, "文档标题不能为空");
        }
        if (file.isEmpty()) {
            throw new BusinessException(400, "请选择PDF文件");
        }
        if (!"application/pdf".equals(file.getContentType())) {
            throw new BusinessException(400, "只支持PDF格式文件");
        }
        if (file.getSize() > 50 * 1024 * 1024) {
            throw new BusinessException(400, "文件大小不能超过50MB");
        }

        KnowledgeBase kb = knowledgeBaseService.getById(kbId);
        if (kb == null) {
            throw new ResourceNotFoundException(404, "知识库不存在");
        }

        try {
            String originalFileName = file.getOriginalFilename();
            int fileSizeKB = (int) Math.ceil((double) file.getSize() / 1024);

            byte[] fileBytes = file.getBytes();
            String pdfText = extractPdfText(fileBytes, originalFileName);
            String fileUrl = fileStorageStrategy.upload(fileBytes, originalFileName, "knowledge");

            KnowledgeDoc doc = new KnowledgeDoc();
            doc.setKbId(kbId);
            doc.setTitle(title.trim());
            doc.setSource("pdf");
            doc.setContent(pdfText);
            doc.setFileName(originalFileName);
            doc.setFileSize(fileSizeKB);
            doc.setFileUrl(fileUrl);
            doc.setChunkCount(0);
            doc.setStatus(0);

            boolean ok = this.save(doc);
            if (!ok) {
                throw new BusinessException(500, "保存文档信息失败");
            }

            knowledgeBaseService.updateStatistics(kbId);
            triggerEmbeddingFromAI(doc.getId(), pdfText, kbId);

            Map<String, Object> result = new HashMap<>();
            result.put("id", doc.getId());
            result.put("fileName", originalFileName);
            result.put("fileSize", fileSizeKB);
            result.put("fileUrl", fileUrl);
            result.put("message", "上传成功，正在解析和向量化...");

            log.info("上传PDF文档成功（使用{}存储策略）: id={}, fileName={}, url={}",
                     fileStorageStrategy.getClass().getSimpleName(),
                     doc.getId(), originalFileName, fileUrl);
            return result;
        } catch (Exception e) {
            log.error("PDF上传失败: {}", e.getMessage(), e);
            throw new BusinessException(500, "文件上传失败: " + e.getMessage());
        }
    }

    @Override
    public boolean removeWithFile(Long id) {
        KnowledgeDoc existing = this.getById(id);
        if (existing == null) {
            throw new ResourceNotFoundException(404, "文档不存在");
        }

        Long kbId = existing.getKbId();

        if ("pdf".equals(existing.getSource()) && existing.getFileUrl() != null && !existing.getFileUrl().isEmpty()) {
            try {
                fileStorageStrategy.delete(existing.getFileUrl());
                log.info("已通过{}删除PDF文件: url={}",
                         fileStorageStrategy.getClass().getSimpleName(),
                         existing.getFileUrl());
            } catch (Exception e) {
                log.warn("删除PDF文件失败: {}, error: {}", existing.getFileName(), e.getMessage());
            }
        }

        boolean ok = this.removeById(id);
        if (ok) {
            knowledgeBaseService.updateStatistics(kbId);
            log.info("删除文档成功: id={}", id);
        }
        return ok;
    }

    private String extractFileNameFromUrl(String fileUrl) {
        if (fileUrl == null || fileUrl.isEmpty()) {
            return "unknown";
        }
        int lastSlash = fileUrl.lastIndexOf('/');
        return lastSlash > 0 ? fileUrl.substring(lastSlash + 1) : fileUrl;
    }

    private int calculateChunkCountFromAI(String content, String source) {
        try {
            if (restTemplate == null) {
                log.info("RestTemplate未注入，使用默认估算");
                return estimateChunkCount(content, source);
            }

            Map<String, String> params = new HashMap<>();
            params.put("content", content);
            params.put("source", source);

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(
                    aiEmbeddingServiceUrl + "/chunkCount",
                    params,
                    Map.class
            );

            if (response != null && response.get("data") != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) response.get("data");
                Object chunkCountObj = data.get("chunkCount");
                if (chunkCountObj instanceof Number) {
                    return ((Number) chunkCountObj).intValue();
                }
            }

            log.warn("AI模块返回分块数异常，使用默认估算");
            return estimateChunkCount(content, source);
        } catch (Exception e) {
            log.warn("调用AI模块计算分块数失败，使用降级方案: {}", e.getMessage());
            return estimateChunkCount(content, source);
        }
    }

    private void triggerEmbeddingFromAI(Long docId, String content, Long knowledgeBaseId) {
        try {
            if (restTemplate == null) {
                log.info("RestTemplate未注入，跳过AI向量化任务: docId={}", docId);
                return;
            }

            Map<String, Object> params = new HashMap<>();
            params.put("docId", docId);
            params.put("content", content);
            params.put("knowledgeBaseId", knowledgeBaseId);

            restTemplate.postForObject(
                    aiEmbeddingServiceUrl + "/process",
                    params,
                    Map.class
            );

            log.info("已向AI模块提交向量化任务: docId={}", docId);
        } catch (Exception e) {
            log.error("调用AI模块向量化失败: docId={}, error={}", docId, e.getMessage());
        }
    }

    private int estimateChunkCount(String content, String source) {
        if (content == null || content.isEmpty()) {
            return 0;
        }
        if ("pdf".equalsIgnoreCase(source)) {
            return (int) Math.ceil((double) content.length() / 400);
        } else {
            return (int) Math.ceil((double) content.length() / 500);
        }
    }

    @Override
    public void updateEmbeddingStatus(Long docId, Integer status, Integer chunkCount, String message) {
        KnowledgeDoc doc = this.getById(docId);
        if (doc == null) {
            log.warn("AI回调：文档不存在，无法更新状态: docId={}", docId);
            return;
        }

        doc.setStatus(status);
        if (chunkCount != null && chunkCount > 0) {
            doc.setChunkCount(chunkCount);
        }

        boolean updated = this.updateById(doc);
        if (updated) {
            log.info("AI回调：文档向量化状态已更新: docId={}, status={}, chunkCount={}, message={}",
                     docId, status, chunkCount, message);

            if (doc.getKbId() != null) {
                knowledgeBaseService.updateStatistics(doc.getKbId());
                log.info("AI回调：已同步更新知识库统计: kbId={}", doc.getKbId());
            }
        } else {
            log.error("AI回调：文档状态更新失败: docId={}", docId);
        }
    }

    private String extractPdfText(byte[] fileBytes, String fileName) {
        try (PDDocument document = PDDocument.load(fileBytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String text = stripper.getText(document);
            if (text == null || text.trim().isEmpty()) {
                log.warn("PDF文件中未提取到文本内容，可能为扫描件或图片型PDF: fileName={}", fileName);
                return "";
            }
            log.info("PDF文本提取成功: fileName={}, textLength={}", fileName, text.length());
            return text;
        } catch (IOException e) {
            log.error("PDF文本提取失败: fileName={}, error={}", fileName, e.getMessage());
            return "";
        }
    }
}