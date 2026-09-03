package com.kzip.app.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 跨域配置：
 * 默认情况下浏览器只暴露 6 个"安全"响应头（Cache-Control / Content-Language / Content-Type /
 * Expires / Last-Modified / Pragma），其它头（如 Content-Disposition、X-Filename）前端 JS 读不到。
 *
 * 通过 addCorsMappings + exposedHeaders 让浏览器把这些头放行，
 * 这样前端 axios/fetch 才能拿到 response.headers.get("Content-Disposition") 里的下载文件名。
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(@NonNull CorsRegistry registry) {
        registry.addMapping("/**")                       // 对所有路径生效
                .allowedOriginPatterns("*")               // 允许所有前端域名（生产建议改成具体域名）
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
                .allowedHeaders("*")                      // 允许前端传任意请求头（Authorization 等）
                .exposedHeaders(                          // ⭐ 关键：暴露给前端 JS 可读的响应头
                        "Content-Disposition",
                        "Content-Length",
                        "Content-Type",
                        "X-Filename"
                )
                .allowCredentials(false)                  // 带 Cookie 时必须 true，并把 originPatterns 改为具体域名
                .maxAge(3600);                            // 预检 OPTIONS 结果缓存 1 小时，减少预检请求
    }
}
