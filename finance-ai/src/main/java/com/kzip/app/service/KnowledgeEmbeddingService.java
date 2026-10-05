package com.kzip.app.service;

public interface KnowledgeEmbeddingService {

    int generateChunkCount(String content, String source);

    void embedDocumentAsync(Long docId, String content, Long knowledgeBaseId);

    /** 同步删除某文档在向量库中的全部向量（按 docId 过滤） */
    void deleteDocumentVectors(Long docId);
}