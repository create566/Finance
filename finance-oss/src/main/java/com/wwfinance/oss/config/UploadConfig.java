package com.wwfinance.oss.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class UploadConfig implements WebMvcConfigurer {

    // 与 FileServiceImpl 返回的文件 URL 前缀保持一致，否则上传成功但访问 404
    @Value("${upload.base-path:D:/桌面/实训/demo/uploads/}")
    private String basePath;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 上传接口返回 /api/oss/file/images/** 路径，经网关路由到本服务后由该 handler 提供文件
        registry.addResourceHandler("/api/oss/file/images/**")
                .addResourceLocations("file:" + basePath);
    }

}