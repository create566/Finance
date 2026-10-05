package com.kzip.app.mongodb;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatMessageDocRepository extends MongoRepository<ChatMessageDoc, String> {

    List<ChatMessageDoc> findBySessionIdOrderByCreateTimeAsc(Long sessionId);

    void deleteBySessionId(Long sessionId);
}