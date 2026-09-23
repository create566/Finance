package com.kzip.app.service;

public interface KnowledgeEmbeddingService {

    int generateChunkCount(String content, String source);

    void embedDocumentAsync(Long docId, String content, Long knowledgeBaseId);
}