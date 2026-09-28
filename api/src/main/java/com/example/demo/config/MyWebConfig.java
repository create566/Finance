package com.example.demo.config;

import com.example.demo.interceptor.JwtAuthInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class MyWebConfig implements WebMvcConfigurer {
    @Autowired
    private JwtAuthInterceptor jwtAuthInterceptor;
    @Autowired
    private RateLimitInterceptor rateLimitInterceptor;

    @Value("${file.storage.local.base-path:./uploads}")
    private String uploadBasePath;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/doc.html")
                .addResourceLocations("classpath:/META-INF/resources/");
        registry.addResourceHandler("/webjars/**")
                .addResourceLocations("classpath:/META-INF/resources/webjars/");

        registry.addResourceHandler("/api/oss/file/images/**")
                .addResourceLocations("file:" + uploadBasePath + "/");
        registry.addResourceHandler("/api/knowledge/files/**")
                .addResourceLocations("file:" + uploadBasePath + "/knowledge/");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtAuthInterceptor)
                .addPathPatterns("/api/**", "/admin/**")
                .excludePathPatterns(
                        "/api/user/hello",
                        "/api/user/login",
                        "/api/user/register",
                        "/api/user/sendCode",
                        "/api/user/loginByCode",
                        "/api/core/userBind/notify",
                        "/api/core/userAccount/notify",
                        "/api/core/userAccount/notifyWithdraw",
                        "/api/core/lendItem/notify",
                        "/api/core/lend/notifyMakeLoan",
                        "/api/core/lendReturn/notifyUrl",
                        "/api/core/user/checkMobile/**",
                        "/admin/core/login",
                        "/admin/core/knowledge/health",
                        "/admin/core/knowledge/doc/*/embedding-status",
                        "/open/ai/health",
                        "/open/ai/prompt/**",
                        "/open/ai/model/**",
                        "/doc.html",
                        "/webjars/**",
                        "/swagger-resources/**",
                        "/v2/api-docs"
                );
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/user/hello",
                        "/api/user/login",
                        "/api/user/register",
                        "/api/user/sendCode",
                        "/api/user/loginByCode"
                );
    }
}