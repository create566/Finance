package com.wwfinance.oss.service.impl;

import com.wwfinance.common.exception.ResourceNotFoundException;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 本地文件存储实现（含归属校验）。
 *
 * <p>存储布局：{@code {upload.base-path}/{category}/u{ownerId}/{fileName}}，
 * URL 与之对应：{@code {upload.url-prefix}/{category}/u{ownerId}/{fileName}}。
 * 「属主段 u&lt;id&gt;」是归属的唯一凭据 —— 删除时只认它，不认前端传来的任何其它信息。</p>
 */
@Slf4j
@Service
public class FileServiceImpl implements FileService {

    /** 允许上传的文件扩展名白名单（防存储型 XSS / webshell） */
    private static final Set<String> ALLOWED_EXTENSIONS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(
                    "jpg", "jpeg", "png", "gif", "bmp", "webp", "pdf")));

    /** 目录名合法字符：字母数字下划线中划线 */
    private static final String SAFE_NAME_REGEX = "[A-Za-z0-9_-]+";

    @Value("${upload.base-path:D:/桌面/实训/demo/uploads/}")
    private String basePath;

    /**
     * 服务根前缀，<b>不含末尾斜杠</b>，例如 {@code /api/oss/file}。
     * 与 {@code UploadConfig} 里 {@code /api/oss/file/images/**} 的资源映射配套。
     */
    @Value("${upload.url-prefix:/api/oss/file}")
    private String urlPrefix;

    @Override
    public String upload(MultipartFile file, String category, Long ownerId) {
        validateFile(file);
        validateCategory(category);
        requireOwner(ownerId);

        String ext = getFileExtension(StringUtils.cleanPath(file.getOriginalFilename()));
        // 文件名前缀用原始 category，避免生成 "images/u33_1712..." 这种带斜杠的名字
        String newFileName = generateFileName(category, ext);
        return store(file, category + "/" + ownerDir(ownerId), newFileName);
    }

    @Override
    public String uploadWithCustomName(MultipartFile file, String category, String customFileName, Long ownerId) {
        validateFile(file);
        validateCategory(category);
        // 自定义文件名同样要做路径穿越校验，否则 ../../../evil.jpg 会写到 basePath 之外
        validateCustomName(customFileName);
        requireOwner(ownerId);

        String ext = getFileExtension(StringUtils.cleanPath(file.getOriginalFilename()));
        return store(file, category + "/" + ownerDir(ownerId), customFileName + ext);
    }

    /** 落盘共用逻辑：目录拼接 → 建目录 → 写文件 → 返回 URL */
    private String store(MultipartFile file, String relativeDir, String fileName) {
        try {
            Path dir = resolveSafePath(relativeDir);
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }
            Path target = dir.resolve(fileName);
            Files.write(target, file.getBytes());

            String fileUrl = urlPrefix + "/" + relativeDir + "/" + fileName;
            log.info("文件上传成功: dir={}, name={}, url={}", relativeDir, fileName, fileUrl);
            return fileUrl;
        } catch (IOException e) {
            log.error("文件上传失败: {}", e.getMessage(), e);
            throw new RuntimeException("文件上传失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteOwned(String fileUrl, Long ownerId) {
        if (ownerId == null) {
            throw new ResourceNotFoundException(401, "请先登录");
        }
        if (!isOwnedBy(fileUrl, ownerId)) {
            // 不区分「文件不存在」与「不是你的」：统一回 403，避免用响应差异探测他人文件是否存在
            log.warn("拒绝删除非本人文件: url={}, ownerId={}", fileUrl, ownerId);
            throw new ResourceNotFoundException(403, "无权删除该文件");
        }
        deleteUnchecked(fileUrl);
    }

    /**
     * 严格匹配 {@code {category}/u{ownerId}/{fileName}} 三段式。
     *
     * <p>历史上传的文件形如 {@code images/xxx.png}（无属主段）不满足三段式，
     * 一律判为「非本人」—— 宁可少删一个旧文件，也不能放过一次跨用户删除。
     * 三段式同时天然排除了 {@code ..} 等路径穿越形态。</p>
     */
    private boolean isOwnedBy(String fileUrl, Long ownerId) {
        if (fileUrl == null || !fileUrl.startsWith(urlPrefix + "/")) {
            return false;
        }
        String relative = fileUrl.substring(urlPrefix.length() + 1);
        String[] segments = relative.split("/");
        return segments.length == 3 && ownerDir(ownerId).equals(segments[1]);
    }

    private void deleteUnchecked(String fileUrl) {
        try {
            Path filePath = resolveSafePath(extractRelativePath(fileUrl));
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
            return Files.exists(resolveSafePath(extractRelativePath(fileUrl)));
        } catch (Exception e) {
            log.warn("检查文件存在性失败: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public Map<String, Object> getFileInfo(String fileUrl) {
        Map<String, Object> info = new HashMap<>();
        try {
            Path filePath = resolveSafePath(extractRelativePath(fileUrl));
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

    /** 把对外 URL 还原成相对路径；前缀不符一律拒绝 */
    private String extractRelativePath(String fileUrl) {
        if (fileUrl == null || !fileUrl.startsWith(urlPrefix + "/")) {
            throw new IllegalArgumentException("无效的文件URL");
        }
        return fileUrl.substring(urlPrefix.length() + 1);
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

    private void requireOwner(Long ownerId) {
        if (ownerId == null) {
            // 归属是这个方法的全部意义，宁可拒绝也不能落一个「无主文件」
            throw new IllegalArgumentException("用户上传必须携带属主 ID");
        }
    }

    /** 属主目录名，带 u 前缀避免与同层的普通目录重名 */
    private static String ownerDir(Long ownerId) {
        return "u" + ownerId;
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
}
