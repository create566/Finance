package com.wwfinance.oss.service.impl;

import com.wwfinance.oss.service.FileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
public class FileServiceImpl implements FileService {

    /** 允许上传的文件扩展名白名单（防存储型 XSS / webshell） */
    private static final java.util.Set<String> ALLOWED_EXTENSIONS = java.util.Collections.unmodifiableSet(
            new java.util.HashSet<>(java.util.Arrays.asList(
                    "jpg", "jpeg", "png", "gif", "bmp", "webp", "pdf")));

    /** 目录名合法字符：字母数字下划线中划线 */
    private static final String SAFE_NAME_REGEX = "[A-Za-z0-9_-]+";

    @Value("${upload.base-path:D:/桌面/实训/demo/uploads/}")
    private String basePath;

    @Value("${upload.url-prefix:/api/oss/file/images/}")
    private String urlPrefix;

    @Override
    public String upload(MultipartFile file, String category) {
        validateFile(file);
        validateCategory(category);

        String originalName = StringUtils.cleanPath(file.getOriginalFilename());
        String ext = getFileExtension(originalName);
        String newFileName = generateFileName(category, ext);

        try {
            // 路径穿越防护：normalize 后必须仍在 basePath 之内
            Path categoryDir = resolveSafePath(category);
            if (!Files.exists(categoryDir)) {
                Files.createDirectories(categoryDir);
            }

            Path filePath = categoryDir.resolve(newFileName);
            Files.write(filePath, file.getBytes());

            String fileUrl = urlPrefix + category + "/" + newFileName;

            log.info("文件上传成功: originalName={}, category={}, url={}", originalName, category, fileUrl);
            return fileUrl;
        } catch (IOException e) {
            log.error("文件上传失败: {}", e.getMessage(), e);
            throw new RuntimeException("文件上传失败: " + e.getMessage(), e);
        }
    }

    @Override
    public String uploadWithCustomName(MultipartFile file, String category, String customFileName) {
        validateFile(file);
        validateCategory(category);
        // 自定义文件名同样要做路径穿越校验，否则 ../../../evil.jsp 会写到 basePath 之外
        validateCustomName(customFileName);

        String originalName = StringUtils.cleanPath(file.getOriginalFilename());
        String ext = getFileExtension(originalName);
        String safeFileName = customFileName + ext;

        try {
            Path categoryDir = resolveSafePath(category);
            if (!Files.exists(categoryDir)) {
                Files.createDirectories(categoryDir);
            }

            Path filePath = categoryDir.resolve(safeFileName);
            Files.write(filePath, file.getBytes());

            String fileUrl = urlPrefix + category + "/" + safeFileName;

            log.info("文件上传成功（自定义名称）: originalName={}, customName={}, url={}",
                     originalName, safeFileName, fileUrl);
            return fileUrl;
        } catch (IOException e) {
            log.error("文件上传失败: {}", e.getMessage(), e);
            throw new RuntimeException("文件上传失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void delete(String fileUrl) {
        try {
            String relativePath = extractRelativePath(fileUrl);
            Path filePath = resolveSafePath(relativePath);

            boolean deleted = Files.deleteIfExists(filePath);
            if (deleted) {
                log.info("文件删除成功: {}", fileUrl);
            } else {
                log.warn("文件不存在，无需删除: {}", fileUrl);
            }
        } catch (Exception e) {
            log.error("删除文件失败: {}, error: {}", fileUrl, e.getMessage());
            throw new RuntimeException("删除文件失败: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean exists(String fileUrl) {
        try {
            String relativePath = extractRelativePath(fileUrl);
            Path filePath = resolveSafePath(relativePath);
            return Files.exists(filePath);
        } catch (Exception e) {
            log.warn("检查文件存在性失败: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public Map<String, Object> getFileInfo(String fileUrl) {
        Map<String, Object> info = new HashMap<>();
        try {
            String relativePath = extractRelativePath(fileUrl);
            Path filePath = resolveSafePath(relativePath);

            if (Files.exists(filePath)) {
                info.put("exists", true);
                info.put("fileName", filePath.getFileName().toString());
                info.put("size", Files.size(filePath));
                info.put("lastModified", Files.getLastModifiedTime(filePath).toString());
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

    /**
     * 路径穿越防护核心：把相对路径解析为绝对路径并 normalize，
     * 若结果逃逸出 basePath 则拒绝。所有文件读写必须经过此方法。
     */
    private Path resolveSafePath(String relativePath) {
        Path base = Paths.get(basePath).toAbsolutePath().normalize();
        Path target = base.resolve(relativePath).normalize();
        if (!target.startsWith(base)) {
            throw new IllegalArgumentException("非法路径：" + relativePath);
        }
        return target;
    }

    private void validateCategory(String category) {
        if (category == null || !category.matches(SAFE_NAME_REGEX)) {
            throw new IllegalArgumentException("目录名只能包含字母、数字、下划线和中划线");
        }
    }

    private void validateCustomName(String customFileName) {
        if (customFileName == null || customFileName.isEmpty()
                || customFileName.contains("/") || customFileName.contains("\\")
                || customFileName.contains("..")) {
            throw new IllegalArgumentException("自定义文件名非法");
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择文件");
        }

        String originalName = StringUtils.cleanPath(file.getOriginalFilename());
        if (originalName.contains("..")) {
            throw new IllegalArgumentException("文件名包含无效路径序列");
        }
        String ext = getFileExtension(originalName).toLowerCase(Locale.ROOT);
        if (!ext.isEmpty() && !ALLOWED_EXTENSIONS.contains(ext.substring(1))) {
            throw new IllegalArgumentException("不支持的文件类型：" + ext);
        }
    }

    private String getFileExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        int dotIdx = fileName.lastIndexOf('.');
        return dotIdx > 0 ? fileName.substring(dotIdx) : "";
    }

    private String generateFileName(String category, String ext) {
        long timestamp = System.currentTimeMillis();
        int random = (int) (Math.random() * 10000);
        return category + "_" + timestamp + "_" + random + ext;
    }

    private String extractRelativePath(String fileUrl) {
        if (fileUrl == null || !fileUrl.startsWith(urlPrefix)) {
            throw new IllegalArgumentException("无效的文件URL");
        }
        return fileUrl.substring(urlPrefix.length());
    }
}