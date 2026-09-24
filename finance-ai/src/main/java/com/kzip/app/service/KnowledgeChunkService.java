package com.kzip.app.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.kzip.app.entity.KnowledgeChunk;

import java.util.List;

public interface KnowledgeChunkService extends IService<KnowledgeChunk> {

    List<KnowledgeChunk> listByDocId(Long docId);

    boolean batchSave(List<KnowledgeChunk> chunks);

    boolean disableByDocId(Long docId);

    boolean toggleStatus(Long id);
}