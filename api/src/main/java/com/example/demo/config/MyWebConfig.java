package com.example.demo.config;

import com.example.demo.interceptor.JwtAuthInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class MyWebConfig  implements WebMvcConfigurer {

    @Autowired
    private JwtAuthInterceptor jwtAuthInterceptor;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 依然可以配置 Swagger 资源
        registry.addResourceHandler("doc.html")
                .addResourceLocations("classpath:/META-INF/resources/");
        registry.addResourceHandler("/webjars/**")
                .addResourceLocations("classpath:/META-INF/resources/webjars/");
        // 这时 Spring Boot 默认的静态资源路径依然有效，不需要额外配置
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtAuthInterceptor)
                // 拦截所有业务接口
                .addPathPatterns("/api/**", "/admin/**")
                // 白名单：登录/注册/测试/swagger 资源
                .excludePathPatterns(
                        "/api/user/hello",                 // 测试接口
                        "/api/user/login",                 // 登录
                        "/api/user/register",              // 注册
                        "/admin/core/user/login",          // 后台登录
                        "/doc.html",
                        "/webjars/**",
                        "/swagger-resources/**",
                        "/v2/api-docs"
                );
    }
}
