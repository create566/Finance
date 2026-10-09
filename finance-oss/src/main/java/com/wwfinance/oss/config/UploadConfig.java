package com.wwfinance.oss.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 已上传文件的静态资源映射。
 *
 * <p>URL 与磁盘的对应关系（2026-10-08 调整）：</p>
 * <pre>
 *   URL   /api/oss/file/images/u33/xxx.png
 *   磁盘  {upload.base-path}/images/u33/xxx.png
 * </pre>
 *
 * <p>改动原因：文件归属现在靠「路径里带属主段 {@code u<userId>}」实现（与 api 模块一致），
 * 落盘位置随之变为 {@code images/u<id>/xxx.png}，因此资源前缀必须补上 {@code images/}，
 * 否则会出现「上传成功但访问 404」。历史上传的 {@code images/xxx.png}（无属主段）
 * 依然可访问，只是 URL 少了一层重复的 {@code images}。</p>
 */
@Configuration
public class UploadConfig implements WebMvcConfigurer {

    // 与 FileServiceImpl 返回的文件 URL 前缀保持一致，否则上传成功但访问 404
    @Value("${upload.base-path:D:/桌面/实训/demo/uploads/}")
    private String basePath;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 去掉 basePath 结尾的斜杠，避免拼出 "uploads//images/"
        String normalized = (basePath.endsWith("/") || basePath.endsWith("\\"))
                ? basePath.substring(0, basePath.length() - 1)
                : basePath;
        // 上传接口返回 /api/oss/file/images/** 路径，经网关路由到本服务后由该 handler 提供文件
        registry.addResourceHandler("/api/oss/file/images/**")
                .addResourceLocations("file:" + normalized + "/images/");
    }

}
