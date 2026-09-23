package com.wwfinance.oss.service;

import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

public interface FileService {

    String upload(MultipartFile file, String category);

    String uploadWithCustomName(MultipartFile file, String category, String customFileName);

    void delete(String fileUrl);

    boolean exists(String fileUrl);

    Map<String, Object> getFileInfo(String fileUrl);
}