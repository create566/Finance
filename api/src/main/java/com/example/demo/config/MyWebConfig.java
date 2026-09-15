package com.example.demo.config;

import com.example.demo.config.RedisRateLimitAspect;
import com.example.demo.interceptor.JwtAuthInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class MyWebConfig implements WebMvcConfigurer {

    @Autowired
    private JwtAuthInterceptor jwtAuthInterceptor;

    @Autowired
    private RedisRateLimitAspect redisRateLimitAspect;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("doc.html")
                .addResourceLocations("classpath:/META-INF/resources/");
        registry.addResourceHandler("/webjars/**")
                .addResourceLocations("classpath:/META-INF/resources/webjars/");
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
                        "/doc.html",
                        "/webjars/**",
                        "/swagger-resources/**",
                        "/v2/api-docs"
                );

        registry.addInterceptor(redisRateLimitAspect)
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
