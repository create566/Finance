package com.kzip.app.repository;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Properties;


@Component
public class LocalPdfFileRepository implements FileRepository {

    private static final Logger log = LoggerFactory.getLogger(LocalPdfFileRepository.class);

    private final VectorStore vectorStore;

    private final Properties chatFiles = new Properties();

    public LocalPdfFileRepository(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }


    @Override
    public boolean save(String chatId, Resource resource) {

        // 2.保存到本地磁盘
        String filename = resource.getFilename();
        File target = new File(Objects.requireNonNull(filename));
        if (!target.exists()) {
            try {
                Files.copy(resource.getInputStream(), target.toPath());
            } catch (IOException e) {
              //  log.error("Failed to save PDF resource.", e);
                return false;
            }
        }
        // 3.保存映射关系
        chatFiles.put(chatId, filename);
        return true;
    }

    @Override
    public Resource getFile(String chatId) {
        return new FileSystemResource(chatFiles.getProperty(chatId));
    }

    @PostConstruct
    private void init() {
        FileSystemResource pdfResource = new FileSystemResource("chat-pdf.properties");
        if (pdfResource.exists()) {
            try {
                chatFiles.load(new BufferedReader(new InputStreamReader(pdfResource.getInputStream(), StandardCharsets.UTF_8)));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        // ChromaVectorStore 由 ChromaDB 持久化，无需从 JSON 加载
        if (vectorStore instanceof org.springframework.ai.vectorstore.SimpleVectorStore simpleStore) {
            FileSystemResource vectorResource = new FileSystemResource("chat-pdf.json");
            if (vectorResource.exists()) {
                simpleStore.load(vectorResource);
            }
        }
    }

    @PreDestroy
    private void persistent() {
        try {
            chatFiles.store(new FileWriter("chat-pdf.properties"), LocalDateTime.now().toString());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        // ChromaVectorStore 由 ChromaDB 持久化，无需保存到 JSON
        if (vectorStore instanceof org.springframework.ai.vectorstore.SimpleVectorStore simpleStore) {
            try {
                simpleStore.save(new File("chat-pdf.json"));
            } catch (Exception e) {
                log.error("Failed to save vector store", e);
            }
        }
    }
}