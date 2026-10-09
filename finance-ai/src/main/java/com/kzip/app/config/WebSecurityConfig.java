package com.kzip.app.config;

import com.kzip.app.repository.LoginTokenStore;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 接口鉴权注册。
 *
 * <p>1. 管理端：/ai/admin/**（后台管理：知识库/提示词/模型/场景绑定）与
 * /ai/knowledge/embedding/**（触发向量化）要求<b>严格管理员登录态</b>：
 * JWT 解析 + token_role=admin + Redis 登录态比对；其中"写"操作再限 SUPER_ADMIN。
 * （2026-10-09 修正：原为"token 非空即放行"，任何伪造 token 头都能篡改全局 AI 配置。）
 *
 * <p>2. 用户端：/ai/chat-sync、/ai/chat-stream、/ai/sessions**、/ai/chat-history/**
 * 要求真实用户登录（JWT 签名 + Redis 登录态校验），修复此前
 * "userId 由前端自报、所有用户共享会话与记忆"的数据串号问题。
 * /ai/chat（早期调试端点）与公开知识库接口保持开放。
 */
@Configuration
public class WebSecurityConfig implements WebMvcConfigurer {

    private final LoginTokenStore loginTokenStore;

    public WebSecurityConfig(LoginTokenStore loginTokenStore) {
        this.loginTokenStore = loginTokenStore;
    }

    @Override
    public void addInterceptors(@NonNull InterceptorRegistry registry) {
        registry.addInterceptor(new AdminAuthInterceptor(loginTokenStore))
                .addPathPatterns(
                        "/ai/admin/**",
                        "/ai/knowledge/embedding/**"
                );
        registry.addInterceptor(new UserAuthInterceptor(loginTokenStore))
                .addPathPatterns(
                        "/ai/chat-sync",
                        "/ai/chat-stream",
                        "/ai/sessions",
                        "/ai/sessions/**",
                        "/ai/chat-history/**"
                );
    }
}