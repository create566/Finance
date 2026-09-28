package com.kzip.app.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.kzip.app.entity.KnowledgeBase;

import java.util.List;

public interface KnowledgeBaseService extends IService<KnowledgeBase> {
    List<KnowledgeBase> listAll(String keyword, Integer status);
    boolean saveBase(KnowledgeBase base);
    boolean updateBase(KnowledgeBase base);
    boolean removeBase(Long id);
    void incrementDocCount(Long kbId, int deltaChunkCount);
    void decrementDocCount(Long kbId, int deltaChunkCount);
    void addChunkCount(Long kbId, int chunkCount);
}