package com.example.demo.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.demo.entity.KnowledgeDoc;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

public interface KnowledgeDocService extends IService<KnowledgeDoc> {

    IPage<KnowledgeDoc> pageList(Long kbId, Integer pageNum, Integer pageSize, String title, String source, Integer status);

    KnowledgeDoc saveTextDoc(KnowledgeDoc doc);

    Map<String, Object> savePdfDoc(Long kbId, String title, MultipartFile file);

    boolean removeWithFile(Long id);

    void updateEmbeddingStatus(Long docId, Integer status, Integer chunkCount, String message);
}