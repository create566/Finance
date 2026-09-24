package com.example.demo.service.strategy.impl;

import com.example.demo.service.strategy.FileStorageStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service("localFileStorage")
public class LocalFileStorageStrategy implements FileStorageStrategy {

    @Value("${file.storage.local.base-path:D:/桌面/实训/demo/uploads}")
    private String basePath;

    @Value("${file.storage.local.url-prefix:/api/knowledge/files}")
    private String urlPrefix;

    @Override
    public String upload(MultipartFile file, String category) {
        String originalFileName = file.getOriginalFilename();
        String extension = getFileExtension(originalFileName);
        String newFileName = generateFileName(category, extension);

        return uploadWithCustomName(file, category, newFileName);
    }

    @Override
    public String upload(byte[] fileBytes, String originalFileName, String category) {
        String extension = getFileExtension(originalFileName);
        String newFileName = generateFileName(category, extension);

        try {
            String categoryPath = basePath + File.separator + category;
            Path dirPath = Paths.get(categoryPath);

            if (!Files.exists(dirPath)) {
                Files.createDirectories(dirPath);
                log.info("创建目录: {}", categoryPath);
            }

            String filePath = categoryPath + File.separator + newFileName;
            Path targetPath = Paths.get(filePath);

            Files.write(targetPath, fileBytes);

            String fileUrl = urlPrefix + "/" + category + "/" + newFileName;

            log.info("本地文件上传成功(byte[]): {} -> {}", newFileName, filePath);
            return fileUrl;
        } catch (IOException e) {
            log.error("本地文件上传失败: {}", e.getMessage(), e);
            throw new RuntimeException("文件上传失败: " + e.getMessage(), e);
        }
    }

    @Override
    public String uploadWithCustomName(MultipartFile file, String category, String customFileName) {
        try {
            String categoryPath = basePath + File.separator + category;
            Path dirPath = Paths.get(categoryPath);

            if (!Files.exists(dirPath)) {
                Files.createDirectories(dirPath);
                log.info("创建目录: {}", categoryPath);
            }

            String filePath = categoryPath + File.separator + customFileName;
            Path targetPath = Paths.get(filePath);

            Files.copy(file.getInputStream(), targetPath);

            String fileUrl = urlPrefix + "/" + category + "/" + customFileName;

            log.info("本地文件上传成功: {} -> {}", customFileName, filePath);
            return fileUrl;
        } catch (IOException e) {
            log.error("本地文件上传失败: {}", e.getMessage(), e);
            throw new RuntimeException("文件上传失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void delete(String fileUrl) {
        try {
            if (fileUrl == null || fileUrl.isEmpty()) {
                return;
            }

            String relativePath = fileUrl.replace(urlPrefix + "/", "");
            String fullPath = basePath + File.separator + relativePath.replace("/", File.separator);

            Path path = Paths.get(fullPath);
            if (Files.exists(path)) {
                Files.delete(path);
                log.info("本地文件删除成功: {}", fullPath);
            } else {
                log.warn("要删除的文件不存在: {}", fullPath);
            }
        } catch (IOException e) {
            log.error("本地文件删除失败: {}", e.getMessage(), e);
            throw new RuntimeException("文件删除失败: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean exists(String fileUrl) {
        try {
            String relativePath = fileUrl.replace(urlPrefix + "/", "");
            String fullPath = basePath + File.separator + relativePath.replace("/", File.separator);
            return Files.exists(Paths.get(fullPath));
        } catch (Exception e) {
            log.error("检查文件存在性失败: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public Map<String, Object> getFileInfo(String fileUrl) {
        Map<String, Object> info = new HashMap<>();
        try {
            String relativePath = fileUrl.replace(urlPrefix + "/", "");
            String fullPath = basePath + File.separator + relativePath.replace("/", File.separator);
            Path path = Paths.get(fullPath);

            if (Files.exists(path)) {
                info.put("exists", true);
                info.put("size", Files.size(path));
                info.put("lastModified", Files.getLastModifiedTime(path).toInstant().toEpochMilli());
                info.put("path", fullPath);
            } else {
                info.put("exists", false);
            }
        } catch (Exception e) {
            log.error("获取文件信息失败: {}", e.getMessage());
            info.put("exists", false);
            info.put("error", e.getMessage());
        }
        return info;
    }

    private String generateFileName(String category, String extension) {
        String timestamp = String.valueOf(Instant.now().toEpochMilli());
        int randomNum = ThreadLocalRandom.current().nextInt(1000, 9999);
        return category + "_" + timestamp + "_" + randomNum + "." + extension;
    }

    private String getFileExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "bin";
        }
        return fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
    }
}