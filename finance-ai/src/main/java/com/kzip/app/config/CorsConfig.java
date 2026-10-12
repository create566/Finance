package com.kzip.app.config;

import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 跨域配置（安全加固版）。
 *
 * <p>作用：
 * <ol>
 *   <li>开发环境下，前端通过 Vite 代理（/ai/** → 8081）同源转发，浏览器不触发跨域；
 *       但为直连 8081 或未来生产跨域场景，仍需明确的白名单。</li>
 *   <li>不再使用 allowedOriginPatterns("*") 全放开，只允许下方精确列出的可信来源，
 *       其它来源的跨域请求会被浏览器拦截（预检失败）。</li>
 *   <li>exposedHeaders 让前端 JS 能读到下载响应头（Content-Disposition 等）。</li>
 * </ol>
 *
 * <p>配置位置：本类（WebMvcConfigurer 方式）即全局生效，等价于 application.yml 中的
 * spring.mvc.cors.* 配置。二者只能保留一处，否则会互相覆盖。当前代码走本类。
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    /**
     * 可信跨域来源白名单（精确匹配，不含通配符）。
     * - 5173：管理后台 Vite 开发服务器；
     * - 5174：用户端前台 Vite 开发服务器；
     * - 8081：本服务自身来源（自请求/健康检查）。
     * 生产环境：必须把真实前端域名（如 https://admin.xxx.com）追加到这里，
     * 否则线上前端将无法跨域访问本服务。凡未列入的来源一律拒绝。
     *
     * 注意：Vite 代理会把浏览器的 Origin 头原样转发（changeOrigin 只改写 Host），
     * 因此即使"同源"经代理转发后，Spring 也会按跨域请求做白名单校验——
     * 用户端 5174 缺席时，浏览器里所有非 GET 请求（POST/DELETE）都会被 403 拒绝。
     */
    private static final List<String> ALLOWED_ORIGINS = List.of(
            "http://localhost:5173",
            "http://127.0.0.1:5173",
            "http://localhost:5174",
            "http://127.0.0.1:5174",
            "http://localhost:8081",
            "http://127.0.0.1:8081",
            // 2026-10-12 新增：公网入口。前端经 frp 暴露到云服务器后，浏览器发出的
            // Origin 是下面的公网地址（而非 localhost），若缺席，所有非 GET 请求
            // （DELETE/POST/PUT，浏览器即使同源也会带 Origin 头）会被本类直接
            // 403 "Invalid CORS request"——表现就是"本机 localhost 能删，别人电脑删不了"。
            // 生产换域名时，把真实域名追加到这里（网关 CorsConfig 里的公网来源与此保持一致）。
            "http://120.26.132.5",     // 用户端入口 :80
            "http://120.26.132.5:81"   // 管理端入口 :81
    );

    @Override
    public void addCorsMappings(@NonNull CorsRegistry registry) {
        registry.addMapping("/**")
                // 精确来源白名单（关键加固点）。withCredentials=true 时 Spring 只允许精确来源，
                // 通配符 * 会被直接拒绝，天然阻止了"任意来源带凭据访问"。
                .allowedOrigins(ALLOWED_ORIGINS.toArray(new String[0]))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
                // 前端会带 token 等自定义请求头；不在白名单内的自定义头会导致预检失败，按需列出。
                .allowedHeaders("Content-Type", "Authorization", "token", "Accept", "X-Requested-With")
                // 暴露给前端 JS 可读的响应头（下载文件名、会话ID等）
                .exposedHeaders(
                        "Content-Disposition",
                        "Content-Length",
                        "Content-Type",
                        "X-Filename",
                        "X-Session-Id"
                )
                // 与前端 axios withCredentials:true 保持一致；仅精确来源白名单可携带凭据
                .allowCredentials(true)
                // 预检 OPTIONS 缓存 1 小时，减少预检请求
                .maxAge(3600);
    }
}
