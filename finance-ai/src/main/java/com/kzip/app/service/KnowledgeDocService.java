package com.kzip.app.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.kzip.app.entity.KnowledgeDoc;

import java.util.List;

public interface KnowledgeDocService extends IService<KnowledgeDoc> {
    List<KnowledgeDoc> listByKbId(Long kbId);
    KnowledgeDoc addTextDoc(Long kbId, String title, String content);
    KnowledgeDoc addPdfDoc(Long kbId, String title, String fileName, Long fileSize);
    boolean removeDoc(Long id);
}