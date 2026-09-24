package com.example.demo.service.strategy;

import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

public interface FileStorageStrategy {

    String upload(MultipartFile file, String category);

    String upload(byte[] fileBytes, String originalFileName, String category);

    String uploadWithCustomName(MultipartFile file, String category, String customFileName);

    void delete(String fileUrl);

    boolean exists(String fileUrl);

    Map<String, Object> getFileInfo(String fileUrl);
}