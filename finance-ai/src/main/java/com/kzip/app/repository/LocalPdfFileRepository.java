package com.kzip.app.repository;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
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
        String filename = resource.getFilename();
        if (filename == null || filename.isEmpty()) {
            filename = "pdf_" + chatId + ".pdf";
        }
        File pdfDir = new File("pdf-storage");
        if (!pdfDir.exists()) {
            pdfDir.mkdirs();
        }
        File target = new File(pdfDir, filename + "_" + System.currentTimeMillis());
        try {
            Files.copy(resource.getInputStream(), target.toPath());
            log.info("PDF文件已保存: {}", target.getAbsolutePath());
        } catch (IOException e) {
            log.error("保存PDF文件失败", e);
            return false;
        }
        chatFiles.put(chatId, target.getPath());
        return true;
    }

    @Override
    public Resource getFile(String chatId) {
        // 修复：未知 chatId 时返回 null（由控制器转 404），
        // 而不是 new FileSystemResource(null) 直接抛 IllegalArgumentException → 500
        String path = chatFiles.getProperty(chatId);
        return path == null ? null : new FileSystemResource(path);
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