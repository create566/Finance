package com.example.demo.controller.api;

import com.example.demo.service.strategy.FileStorageStrategy;
import com.wwfinance.common.result.PccAjaxResult;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 文件上传（本地存储策略，供实名认证等模块上传身份证照片）
 */
@Slf4j
@Api(tags = "文件上传")
@RestController
@RequestMapping("/api/oss/file")
public class OssFileController {

    private static final long MAX_IMAGE_SIZE = 10 * 1024 * 1024;

    private static final Set<String> IMAGE_EXTENSIONS = new HashSet<>(
            Arrays.asList("jpg", "jpeg", "png", "gif", "bmp", "webp"));

    @Autowired
    private FileStorageStrategy fileStorageStrategy;

    @Value("${file.storage.local.url-prefix:/api/knowledge/files}")
    private String urlPrefix;

    @ApiOperation("上传图片文件")
    @PostMapping("/upload")
    public PccAjaxResult upload(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return new PccAjaxResult(400, "请选择要上传的文件", null);
        }
        if (file.getSize() > MAX_IMAGE_SIZE) {
            return new PccAjaxResult(400, "文件大小不能超过10MB", null);
        }
        String extension = getExtension(file.getOriginalFilename());
        if (!IMAGE_EXTENSIONS.contains(extension)) {
            return new PccAjaxResult(400, "仅支持图片格式：jpg/jpeg/png/gif/bmp/webp", null);
        }
        String fileUrl = fileStorageStrategy.upload(file, "images");
        log.info("图片上传成功: {} ({}KB)", fileUrl, file.getSize() / 1024);
        return new PccAjaxResult(200, "上传成功", fileUrl);
    }

    @ApiOperation("删除已上传的文件")
    @DeleteMapping("/remove")
    public PccAjaxResult remove(@RequestParam("url") String url) {
        if (url == null || !url.startsWith(urlPrefix + "/") || url.contains("..")) {
            return new PccAjaxResult(400, "非法的文件地址", null);
        }
        fileStorageStrategy.delete(url);
        return new PccAjaxResult(200, "删除成功", null);
    }

    private String getExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase(Locale.ROOT);
    }
}