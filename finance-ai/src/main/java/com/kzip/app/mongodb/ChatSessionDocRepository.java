package com.kzip.app.mongodb;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ChatSessionDocRepository extends MongoRepository<ChatSessionDoc, Long> {
}
