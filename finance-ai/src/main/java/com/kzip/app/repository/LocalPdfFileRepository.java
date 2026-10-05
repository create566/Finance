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
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Properties;


@Component
public class LocalPdfFileRepository implements FileRepository {

    private static final Logger log = LoggerFactory.getLogger(LocalPdfFileRepository.class);

    private final VectorStore vectorStore;

    private final Properties chatFiles = new Properties();

    /** 保护 chatFiles 的复合读写（put/get/store），避免多线程并发访问不一致 */
    private final Object fileLock = new Object();

    public LocalPdfFileRepository(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }


    @Override
    public boolean save(String chatId, Resource resource) {
        // 防御性清洗：文件名来自用户上传，可能包含路径分隔符/.. /非法字符，
        // 统一替换清洗后再拼接，杜绝路径穿越与非法文件名。
        String filename = sanitizeFilename(resource.getFilename());
        if (filename == null || filename.isEmpty()) {
            // chatId 也是 URL 路径片段，可能夹带路径穿越字符，同样需清洗
            filename = "pdf_" + sanitizeFilename(chatId) + ".pdf";
        }
        File pdfDir = new File("pdf-storage");
        if (!pdfDir.exists() && !pdfDir.mkdirs()) {
            log.error("无法创建PDF存储目录: {}", pdfDir.getAbsolutePath());
            return false;
        }
        File target = new File(pdfDir, filename + "_" + System.currentTimeMillis());
        // 兜底：清洗后仍校验解析结果必须落在 pdfDir 内，防止任何形式的越界写入
        if (!pdfDir.getAbsoluteFile().equals(target.getParentFile().getAbsoluteFile())) {
            log.warn("文件名清洗后仍越出存储目录，拒绝保存: filename={}", filename);
            return false;
        }
        // try-with-resources 关闭输入流，避免句柄泄漏
        try (InputStream in = resource.getInputStream()) {
            Files.copy(in, target.toPath());
            log.info("PDF文件已保存: {}", target.getAbsolutePath());
        } catch (IOException e) {
            log.error("保存PDF文件失败", e);
            return false;
        }
        synchronized (fileLock) {
            chatFiles.put(chatId, target.getPath());
        }
        return true;
    }

    /**
     * 清洗上传文件名：去除路径分隔符、Windows 非法字符与控制字符，
     * 去除连续 ".."（防路径穿越），并限制长度。返回空字符串表示无有效文件名。
     */
    private String sanitizeFilename(String raw) {
        if (raw == null) {
            return "";
        }
        // 1) 路径分隔符 → 下划线，阻断目录穿越
        String name = raw.replace('\\', '_').replace('/', '_');
        // 2) Windows 非法字符 + 控制字符 → 下划线
        name = name.replaceAll("[<>:\"|?*\\x00-\\x1F]", "_");
        // 3) 连续点（含 ".."）→ 下划线，阻断路径穿越
        name = name.replace("..", "_");
        // 4) 去首尾空白
        name = name.trim();
        // 5) 限制长度，防止超长文件名
        if (name.length() > 200) {
            name = name.substring(0, 200);
        }
        return name;
    }

    @Override
    public Resource getFile(String chatId) {
        // 修复：未知 chatId 时返回 null（由控制器转 404），
        // 而不是 new FileSystemResource(null) 直接抛 IllegalArgumentException → 500
        String path;
        synchronized (fileLock) {
            path = chatFiles.getProperty(chatId);
        }
        return path == null ? null : new FileSystemResource(path);
    }

    @PostConstruct
    private void init() {
        FileSystemResource pdfResource = new FileSystemResource("chat-pdf.properties");
        if (pdfResource.exists()) {
            // try-with-resources 确保 Reader/InputStream 被关闭，避免启动时句柄泄漏
            try (InputStream in = pdfResource.getInputStream();
                 Reader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                synchronized (fileLock) {
                    chatFiles.load(reader);
                }
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
        try (Writer writer = Files.newBufferedWriter(Paths.get("chat-pdf.properties"),
                StandardCharsets.UTF_8)) {
            synchronized (fileLock) {
                chatFiles.store(writer, LocalDateTime.now().toString());
            }
        } catch (IOException e) {
            throw new RuntimeException("保存 chat-pdf.properties 失败", e);
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