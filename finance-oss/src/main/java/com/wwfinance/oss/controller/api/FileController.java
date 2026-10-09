package com.wwfinance.oss.controller.api;

import com.wwfinance.common.exception.ResourceNotFoundException;
import com.wwfinance.common.result.PccAjaxResult;
import com.wwfinance.oss.service.FileService;
import com.wwfinance.common.utils.LoginUserContext;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.util.Map;

/**
 * 文件存储服务（finance-oss）。
 *
 * <p><b>2026-10-08 安全修复</b>。修复前的三个问题：</p>
 * <ol>
 *   <li>上传/删除/存在性/信息 <b>全都不校验登录</b>（本模块当时没有拦截器）；</li>
 *   <li>删除只按 URL 前缀定位文件，<b>不校验归属</b> ⇒ 任意人拿到 URL 即可删除他人文件（IDOR）；</li>
 *   <li>{@code category} 由前端可传，等于让外部决定落盘目录。</li>
 * </ol>
 * <p>修复方式：登录态由 {@code JwtAuthInterceptor} 统一把关（见 {@code OssWebConfig}），
 * 属主由 {@code LoginUserContext} 取值并<b>编进存储路径</b>，删除时按路径段校验。</p>
 *
 * <p><b>2026-10-08 起本模块是「文件上传/删除」的唯一实现</b>：api 模块里功能重复的
 * {@code OssFileController} 已删除（它只收图片、不收 category，是本模块功能的真子集）。
 * 经网关 {@code /*}{@code /oss/**} 进来的文件请求全部落到本模块。
 * api 模块仍保留 {@code FileStorageStrategy}，但仅供其<b>进程内</b>业务使用
 * （合同签名图、知识库 PDF），不再是 HTTP 端点。</p>
 *
 * <p>扩展名白名单沿用本模块自身口径（图片 + pdf），比已删除的 api 版更宽。</p>
 */
@Slf4j
@Api(tags = "文件存储服务")
@RestController
@RequestMapping("/api/oss/file")
public class FileController {

    /** 单文件大小上限，与 api 模块保持一致 */
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;

    @Resource
    private FileService fileService;

    /** 存活探针：不校验登录，用于确认服务已被网关正确路由 */
    @GetMapping("/hello")
    public String hello() {
        return "<h2>OSS Service is Running</h2>";
    }

    @ApiOperation("文件上传（自动生成文件名，按登录用户归属）")
    @PostMapping("/upload")
    public PccAjaxResult upload(@RequestParam("file") MultipartFile file,
                                @RequestParam(value = "category", defaultValue = "images") String category) {
        Integer userId = LoginUserContext.getUserid();
        if (userId == null) {
            return new PccAjaxResult(401, "请先登录", null);
        }
        if (file == null || file.isEmpty()) {
            return new PccAjaxResult(400, "请选择要上传的文件", null);
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            return new PccAjaxResult(400, "文件大小不能超过10MB", null);
        }
        try {
            // 属主随文件一起落盘（images/u<id>/…），删除时据此判定，不再依赖前端传参
            String url = fileService.upload(file, category, userId.longValue());
            log.info("文件上传成功: {} ({}KB) by user={}", url, file.getSize() / 1024, userId);
            return new PccAjaxResult(200, "上传成功", url);
        } catch (IllegalArgumentException e) {
            return new PccAjaxResult(400, e.getMessage(), null);
        } catch (Exception e) {
            return new PccAjaxResult(500, "上传失败: " + e.getMessage(), null);
        }
    }

    @ApiOperation("文件上传（自定义文件名，按登录用户归属）")
    @PostMapping("/upload/custom")
    public PccAjaxResult uploadWithCustomName(@RequestParam("file") MultipartFile file,
                                              @RequestParam(value = "category", defaultValue = "images") String category,
                                              @RequestParam("fileName") String fileName) {
        Integer userId = LoginUserContext.getUserid();
        if (userId == null) {
            return new PccAjaxResult(401, "请先登录", null);
        }
        if (file == null || file.isEmpty()) {
            return new PccAjaxResult(400, "请选择要上传的文件", null);
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            return new PccAjaxResult(400, "文件大小不能超过10MB", null);
        }
        try {
            String url = fileService.uploadWithCustomName(file, category, fileName, userId.longValue());
            return new PccAjaxResult(200, "上传成功", url);
        } catch (IllegalArgumentException e) {
            return new PccAjaxResult(400, e.getMessage(), null);
        } catch (Exception e) {
            return new PccAjaxResult(500, "上传失败: " + e.getMessage(), null);
        }
    }

    @ApiOperation("删除文件（仅限本人上传的文件）")
    @DeleteMapping("/remove")
    public PccAjaxResult delete(@RequestParam("url") String url) {
        Integer userId = LoginUserContext.getUserid();
        if (userId == null) {
            return new PccAjaxResult(401, "请先登录", null);
        }
        if (url == null || url.contains("..")) {
            return new PccAjaxResult(400, "非法的文件地址", null);
        }
        try {
            fileService.deleteOwned(url, userId.longValue());
        } catch (ResourceNotFoundException e) {
            return new PccAjaxResult(e.getCode(), e.getMsg(), null);
        }
        return new PccAjaxResult(200, "删除成功", null);
    }

    @ApiOperation("检查文件是否存在")
    @GetMapping("/exists")
    public PccAjaxResult exists(@RequestParam("url") String url) {
        return new PccAjaxResult(200, "查询成功", fileService.exists(url));
    }

    @ApiOperation("获取文件信息")
    @GetMapping("/info")
    public PccAjaxResult getFileInfo(@RequestParam("url") String url) {
        Map<String, Object> info = fileService.getFileInfo(url);
        return new PccAjaxResult(200, "查询成功", info);
    }
}
