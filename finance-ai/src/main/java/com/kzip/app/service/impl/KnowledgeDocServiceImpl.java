package com.kzip.app.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kzip.app.entity.KnowledgeDoc;
import com.kzip.app.mapper.KnowledgeDocMapper;
import com.kzip.app.service.KnowledgeBaseService;
import com.kzip.app.service.KnowledgeDocService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class KnowledgeDocServiceImpl extends ServiceImpl<KnowledgeDocMapper, KnowledgeDoc> implements KnowledgeDocService {

    @Autowired
    private KnowledgeBaseService knowledgeBaseService;

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
        doc.setStatus(1);
        this.save(doc);
        knowledgeBaseService.incrementDocCount(kbId, doc.getChunkCount());
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
        doc.setFileSize(fileSize);
        doc.setChunkCount(Math.max(1, (int) Math.ceil(fileSize / 50.0)));
        doc.setStatus(0);
        this.save(doc);
        knowledgeBaseService.incrementDocCount(kbId, doc.getChunkCount());
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