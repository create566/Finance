package com.kzip.app.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kzip.app.entity.KnowledgeDoc;
import com.kzip.app.mapper.KnowledgeDocMapper;
import com.kzip.app.service.KnowledgeBaseService;
import com.kzip.app.service.KnowledgeDocService;
import com.kzip.app.service.KnowledgeEmbeddingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
public class KnowledgeDocServiceImpl extends ServiceImpl<KnowledgeDocMapper, KnowledgeDoc> implements KnowledgeDocService {

    @Autowired
    private KnowledgeBaseService knowledgeBaseService;

    // @Lazy：KnowledgeEmbeddingServiceImpl 依赖 KnowledgeDocService，形成循环，延迟注入解开
    @Autowired
    @org.springframework.context.annotation.Lazy
    private KnowledgeEmbeddingService knowledgeEmbeddingService;

    @Override
    public List<KnowledgeDoc> listByKbId(Long kbId) {
        LambdaQueryWrapper<KnowledgeDoc> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KnowledgeDoc::getKbId, kbId);
        wrapper.orderByDesc(KnowledgeDoc::getCreateTime);
        return this.list(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeDoc addTextDoc(Long kbId, String title, String content) {
        KnowledgeDoc doc = new KnowledgeDoc();
        doc.setKbId(kbId);
        doc.setTitle(title);
        doc.setSource("text");
        doc.setContent(content);
        doc.setFileName("");
        doc.setFileSize(0L);
        doc.setChunkCount(Math.max(1, (int) Math.ceil(content.length() / 500.0)));
        // 修复：文本知识同样要做向量化，否则 RAG 永远检索不到却显示"就绪"。
        // 与 PDF 流程一致：先置 0（处理中），异步向量化成功后由嵌入服务置 1
        doc.setStatus(0);
        this.save(doc);
        knowledgeBaseService.incrementDocCount(kbId, 0);
        // 事务提交后触发异步向量化（embedDocumentAsync 会回查 doc 并更新状态）
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        try {
                            knowledgeEmbeddingService.embedDocumentAsync(doc.getId(), content, kbId);
                        } catch (Exception e) {
                            log.error("文本知识向量化触发失败: docId={}, error={}", doc.getId(), e.getMessage(), e);
                            doc.setStatus(2);
                            updateById(doc);
                        }
                    }
                });
        return doc;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeDoc addPdfDoc(Long kbId, String title, String fileName, Long fileSize) {
        KnowledgeDoc doc = new KnowledgeDoc();
        doc.setKbId(kbId);
        doc.setTitle(title);
        doc.setSource("pdf");
        doc.setContent("");
        doc.setFileName(fileName);
        doc.setFileSize(Math.max(1, fileSize / 1024));
        doc.setChunkCount(0);
        doc.setStatus(0);
        this.save(doc);
        knowledgeBaseService.incrementDocCount(kbId, 0);
        return doc;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeDoc(Long id) {
        KnowledgeDoc doc = this.getById(id);
        if (doc == null) {
            return false;
        }
        boolean ok = this.removeById(id);
        if (ok) {
            knowledgeBaseService.decrementDocCount(doc.getKbId(), doc.getChunkCount() == null ? 0 : doc.getChunkCount());
        }
        return ok;
    }
}