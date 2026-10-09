package com.example.demo.config;

import com.wwfinance.common.auth.JwtAuthInterceptor;
import com.wwfinance.common.auth.JwtAuthInterceptor.AdminPolicy;
import com.wwfinance.common.auth.JwtAuthInterceptor.PathRule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

/**
 * api 的 Web 配置。
 *
 * <p><b>2026-10-08</b>：{@code JwtAuthInterceptor} 已上移到 finance-common，主链共享；
 * api 侧只保留自己的「角色-路径准入策略」（原先硬编码在 api 版拦截器里），
 * 通过 {@link PathRule} 列表在这里声明。</p>
 */
@Configuration
@Import(com.wwfinance.common.config.JwtSecretConfig.class)
public class MyWebConfig implements WebMvcConfigurer {

    @Autowired
    private JwtAuthInterceptor jwtAuthInterceptor;
    @Autowired
    private RateLimitInterceptor rateLimitInterceptor;

    @Value("${file.storage.local.base-path:./uploads}")
    private String uploadBasePath;

    /**
     * api 的准入策略（与迁移前逐条等价）：
     * <ul>
     *   <li>{@code /admin/**} 必须管理员，否则 403「无权限访问后台接口」；</li>
     *   <li>{@code /api/**} 禁止管理员，否则 403「管理员请使用后台管理接口」。</li>
     * </ul>
     * 本拦截器只注册在 {@code /api/**} 与 {@code /admin/**} 上，其余路径不会被拦到。
     */
    @Bean
    public JwtAuthInterceptor jwtAuthInterceptor(StringRedisTemplate stringRedisTemplate) {
        return new JwtAuthInterceptor(stringRedisTemplate, Arrays.asList(
                new PathRule("/admin/", AdminPolicy.REQUIRED, "无权限访问后台接口"),
                new PathRule("/api/", AdminPolicy.FORBIDDEN, "管理员请使用后台管理接口")), true);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/doc.html")
                .addResourceLocations("classpath:/META-INF/resources/");
        registry.addResourceHandler("/webjars/**")
                .addResourceLocations("classpath:/META-INF/resources/webjars/");

        registry.addResourceHandler("/api/oss/file/images/**")
                .addResourceLocations("file:" + uploadBasePath + "/images/");
        registry.addResourceHandler("/api/knowledge/files/**")
                .addResourceLocations("file:" + uploadBasePath + "/");
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
                        "/api/oss/file/images/**",
                        "/api/knowledge/files/**",
                        "/admin/core/login",
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
