package com.kzip.app.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;

/**
 * 管理端接口鉴权拦截器。
 *
 * <p>背景：finance-ai 模块此前所有 /ai/admin/** 与 /ai/knowledge/embedding/** 接口均无任何鉴权，
 * 任何人可直接调用，篡改提示词/模型/知识库或触发向量化。本拦截器要求请求必须携带登录 token。
 *
 * <p>校验规则：前端 axios（src/utils/request.ts）会在 header 带 token：
 * <pre>{@code config.headers.set('token', token);}</pre>
 * 因此这里校验请求头 token 非空。
 *
 * <p style="color:red"><b>注意：</b>当前为"非空即放行"的最小鉴权，用于封堵"完全无鉴权"的漏洞。
 * 生产环境务必替换为对 gateway 签发的 JWT 做<b>签名 + 过期 + 角色</b>校验
 * （需引入 jjwt 依赖并配置与网关一致的密钥/公钥），并把期望值抽到配置项。
 */
public class AdminAuthInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AdminAuthInterceptor.class);

    /** 前端使用的登录 token 请求头名 */
    private static final String TOKEN_HEADER = "token";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        // 放行 CORS 预检请求，避免 OPTIONS 被拦截
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String token = request.getHeader(TOKEN_HEADER);
        if (token != null && !token.trim().isEmpty()) {
            return true;
        }

        log.warn("未携带token访问管理接口被拦截 [{} {}]", request.getMethod(), request.getRequestURI());
        writeUnauthorized(response);
        return false;
    }

    private void writeUnauthorized(HttpServletResponse response) throws Exception {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":401,\"message\":\"未认证，请先登录\"}");
    }
}