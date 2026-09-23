package com.wwfinance.oss.controller.api;

import com.wwfinance.common.result.PccAjaxResult;
import com.wwfinance.oss.service.FileService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.util.Map;

@Api(tags = "文件存储服务")
@RestController
@RequestMapping("/api/oss/file")
public class FileController {

    @Resource
    private FileService fileService;

    @GetMapping("/hello")
    public String hello() {
        return "<h2>OSS Service is Running</h2>";
    }

    @ApiOperation("文件上传（自动分类）")
    @PostMapping("/upload")
    public PccAjaxResult upload(@RequestParam("file") MultipartFile file,
                                 @RequestParam(value = "category", defaultValue = "general") String category) {
        try {
            String url = fileService.upload(file, category);
            return new PccAjaxResult(200, "上传成功", url);
        } catch (IllegalArgumentException e) {
            return new PccAjaxResult(400, e.getMessage(), null);
        } catch (Exception e) {
            return new PccAjaxResult(500, "上传失败: " + e.getMessage(), null);
        }
    }

    @ApiOperation("文件上传（自定义文件名）")
    @PostMapping("/upload/custom")
    public PccAjaxResult uploadWithCustomName(@RequestParam("file") MultipartFile file,
                                               @RequestParam("category") String category,
                                               @RequestParam("fileName") String fileName) {
        try {
            String url = fileService.uploadWithCustomName(file, category, fileName);
            return new PccAjaxResult(200, "上传成功", url);
        } catch (IllegalArgumentException e) {
            return new PccAjaxResult(400, e.getMessage(), null);
        } catch (Exception e) {
            return new PccAjaxResult(500, "上传失败: " + e.getMessage(), null);
        }
    }

    @ApiOperation("删除文件")
    @DeleteMapping("/remove")
    public PccAjaxResult delete(@RequestParam("url") String url) {
        try {
            fileService.delete(url);
            return new PccAjaxResult(200, "删除成功", null);
        } catch (Exception e) {
            return new PccAjaxResult(500, "删除失败: " + e.getMessage(), null);
        }
    }

    @ApiOperation("检查文件是否存在")
    @GetMapping("/exists")
    public PccAjaxResult exists(@RequestParam("url") String url) {
        boolean exists = fileService.exists(url);
        return new PccAjaxResult(200, "查询成功", exists);
    }

    @ApiOperation("获取文件信息")
    @GetMapping("/info")
    public PccAjaxResult getFileInfo(@RequestParam("url") String url) {
        Map<String, Object> info = fileService.getFileInfo(url);
        return new PccAjaxResult(200, "查询成功", info);
    }
}