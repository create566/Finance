package com.example.demo.service.strategy.impl;

import com.example.demo.service.strategy.FileStorageStrategy;
import com.wwfinance.common.exception.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

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
    public String upload(MultipartFile file, String category, Long ownerId) {
        if (ownerId == null) {
            // 归属是这个方法的全部意义，宁可拒绝也不能落一个"无主文件"
            throw new IllegalArgumentException("用户上传必须携带属主 ID");
        }
        String extension = getFileExtension(file.getOriginalFilename());
        // 文件名里的前缀用原始 category，避免生成 "images/u33_1712..." 这种带斜杠的名字
        String newFileName = generateFileName(category, extension);

        // 关键：属主编进存储路径，归属自证，删除时据此校验，无需额外建表
        return uploadWithCustomName(file, category + "/" + ownerDir(ownerId), newFileName);
    }

    @Override
    public String upload(byte[] fileBytes, String originalFileName, String category) {
        String extension = getFileExtension(originalFileName);
        String newFileName = generateFileName(category, extension);

        try {
            // 路径穿越防护：目录与文件名一并经 normalize 校验，越出 basePath 即拒绝
            Path dirPath = resolveSafePath(category);

            if (!Files.exists(dirPath)) {
                Files.createDirectories(dirPath);
                log.info("创建目录: {}", dirPath);
            }

            Path targetPath = resolveSafePath(category + "/" + newFileName);

            Files.write(targetPath, fileBytes);

            String fileUrl = urlPrefix + "/" + category + "/" + newFileName;

            log.info("本地文件上传成功(byte[]): {} -> {}", newFileName, targetPath);
            return fileUrl;
        } catch (IOException e) {
            log.error("本地文件上传失败: {}", e.getMessage(), e);
            throw new RuntimeException("文件上传失败: " + e.getMessage(), e);
        }
    }

    @Override
    public String uploadWithCustomName(MultipartFile file, String category, String customFileName) {
        try {
            // category 允许带子目录（如 images/u33）；resolveSafePath 同时完成拼盘与越界校验
            Path dirPath = resolveSafePath(category);

            if (!Files.exists(dirPath)) {
                Files.createDirectories(dirPath);
                log.info("创建目录: {}", dirPath);
            }

            Path targetPath = resolveSafePath(category + "/" + customFileName);

            Files.copy(file.getInputStream(), targetPath);

            String fileUrl = urlPrefix + "/" + category + "/" + customFileName;

            log.info("本地文件上传成功: {} -> {}", customFileName, targetPath);
            return fileUrl;
        } catch (IOException e) {
            log.error("本地文件上传失败: {}", e.getMessage(), e);
            throw new RuntimeException("文件上传失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteOwned(String fileUrl, Long ownerId) {
        if (ownerId == null) {
            throw new ResourceNotFoundException(401, "请先登录");
        }
        if (!isOwnedBy(fileUrl, ownerId)) {
            // 不区分"文件不存在"与"不是你的"：统一回 403，避免用响应差异探测他人文件是否存在
            log.warn("拒绝删除非本人文件: url={}, ownerId={}", fileUrl, ownerId);
            throw new ResourceNotFoundException(403, "无权删除该文件");
        }
        deleteUnchecked(fileUrl);
    }

    /**
     * 严格匹配 {@code <category>/u<ownerId>/<fileName>} 三段式。
     *
     * <p>历史上传的文件形如 {@code images/xxx.png}（无属主段）不满足三段式，
     * 一律判为"非本人"—— 宁可少删一个旧文件，也不能放过一次跨用户删除。
     * 同时三段式天然排除了 {@code ..} 等路径穿越形态。</p>
     */
    private boolean isOwnedBy(String fileUrl, Long ownerId) {
        if (fileUrl == null || !fileUrl.startsWith(urlPrefix + "/")) {
            return false;
        }
        String relative = fileUrl.substring(urlPrefix.length() + 1);
        String[] segments = relative.split("/");
        return segments.length == 3 && ownerDir(ownerId).equals(segments[1]);
    }

    /** 属主目录名，带 {@code u} 前缀避免与同层的普通目录重名 */
    private static String ownerDir(Long ownerId) {
        return "u" + ownerId;
    }

    @Override
    public void deleteUnchecked(String fileUrl) {
        try {
            if (fileUrl == null || fileUrl.isEmpty()) {
                return;
            }

            String relativePath = fileUrl.replace(urlPrefix + "/", "");
            Path path = resolveSafePath(relativePath);

            if (Files.exists(path)) {
                Files.delete(path);
                log.info("本地文件删除成功: {}", path);
            } else {
                log.warn("要删除的文件不存在: {}", path);
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
            return Files.exists(resolveSafePath(relativePath));
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
            Path path = resolveSafePath(relativePath);

            if (Files.exists(path)) {
                info.put("exists", true);
                info.put("size", Files.size(path));
                info.put("lastModified", Files.getLastModifiedTime(path).toInstant().toEpochMilli());
                info.put("path", path.toString());
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
     *
     * <p>与 finance-oss 的 {@code FileServiceImpl#resolveSafePath} 同构。此前 api 版本只有
     * 「前端传参里含 {@code ..} 就拒绝」这类粗检，而 category / 自定义文件名 / URL 三段
     * 都是直接字符串拼接，任何一段带分隔符即可越界。</p>
     */
    private Path resolveSafePath(String relativePath) {
        Path base = Paths.get(basePath).toAbsolutePath().normalize();
        Path target = base.resolve(relativePath).normalize();
        if (!target.startsWith(base)) {
            log.warn("拒绝越界路径: base={}, relative={}", base, relativePath);
            throw new IllegalArgumentException("非法路径：" + relativePath);
        }
        return target;
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