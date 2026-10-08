package com.example.demo.controller.api;

import com.example.demo.service.strategy.FileStorageStrategy;
import com.example.demo.utils.LoginUserContext;
import com.wwfinance.common.exception.ResourceNotFoundException;
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
 *
 * <p>文件归属：上传时把当前登录用户 ID 编进存储路径（{@code images/u<userId>/...}），
 * 删除时校验该路径段。这样"我能删谁的文件"由登录态决定，而不是由请求里传的 URL 决定 ——
 * 历史版本只校验 URL 前缀，导致任意登录用户都能删掉他人的实名材料与合同签名存证。</p>
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
        Integer userId = LoginUserContext.getUserid();
        if (userId == null) {
            return new PccAjaxResult(401, "请先登录", null);
        }
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
        // 归属随文件一起落盘：删除时据此判定，不再依赖前端传参
        String fileUrl = fileStorageStrategy.upload(file, "images", userId.longValue());
        log.info("图片上传成功: {} ({}KB) by user={}", fileUrl, file.getSize() / 1024, userId);
        return new PccAjaxResult(200, "上传成功", fileUrl);
    }

    @ApiOperation("删除已上传的文件（仅限本人上传的图片）")
    @DeleteMapping("/remove")
    public PccAjaxResult remove(@RequestParam("url") String url) {
        Integer userId = LoginUserContext.getUserid();
        if (userId == null) {
            return new PccAjaxResult(401, "请先登录", null);
        }
        if (url == null || !url.startsWith(urlPrefix + "/") || url.contains("..")) {
            return new PccAjaxResult(400, "非法的文件地址", null);
        }
        try {
            fileStorageStrategy.deleteOwned(url, userId.longValue());
        } catch (ResourceNotFoundException e) {
            return new PccAjaxResult(e.getCode(), e.getMsg(), null);
        }
        return new PccAjaxResult(200, "删除成功", null);
    }

    private String getExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase(Locale.ROOT);
    }
}
