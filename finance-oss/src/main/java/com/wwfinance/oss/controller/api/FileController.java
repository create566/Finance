package com.wwfinance.oss.controller.api;


import com.wwfinance.common.result.PccAjaxResult;
import com.wwfinance.oss.service.FileService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Api(tags = "文件上传")
//@CrossOrigin //跨域
@RestController
@RequestMapping("/api/oss/file")
public class FileController {

    @Resource
    private FileService fileService;

    private static String UPLOADED_FOLDER = "D:/uploads/";

    @GetMapping("/hello")
    public String hello() {
        return "<h2>hello</h2>";
    }

    @ApiOperation("文件上传")
    @PostMapping("/upload")
    public PccAjaxResult singleFileUpload(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return new PccAjaxResult(500, "请选择一个文件进行上传");
        }
        try {
            // 获取文件名
            String fileName = StringUtils.cleanPath(file.getOriginalFilename());
            // 检查文件是否是目录中的子文件
            if (fileName.contains("..")) {
                return new PccAjaxResult(500, "不好意思！文件名包含无效的路径序列");
            }
            // 将文件拷贝到指定目标文件夹
            byte[] bytes = file.getBytes();
            Path path = Paths.get(UPLOADED_FOLDER + fileName);
            Files.write(path, bytes);
            return new PccAjaxResult(200, "文件上传成功", path);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return new PccAjaxResult(500, "文件上传失败");
    }

    @ApiOperation("删除oss文件")
    @DeleteMapping("/remove")
    public PccAjaxResult deleteFile(@RequestParam("url") String url) {
        try {
            // 删除文件
            System.out.println(url);
            Files.deleteIfExists(Paths.get(URI.create(url)));
            return new PccAjaxResult(200, "文件删除成功");
        } catch (Exception e) {
            e.printStackTrace();
            return new PccAjaxResult(500, "文件删除失败");
        }
    }

    public static void main(String[] args) throws IOException {
        Files.deleteIfExists(Paths.get(URI.create("file:///D:/uploads/1.png")));
    }
}
