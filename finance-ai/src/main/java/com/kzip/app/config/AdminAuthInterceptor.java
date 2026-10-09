package com.kzip.app.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kzip.app.repository.LoginTokenStore;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 管理端接口鉴权拦截器（严格版）。
 *
 * <p><b>2026-10-09 重写（P2-11 同源问题，危险度更高）</b>。
 * 原实现是"请求头 token <b>非空即放行</b>"（见文件历史注释"当前为最小鉴权"）——
 * 这意味着任何人只要带一个 {@code token: x} 头，就能调用 {@code /ai/admin/**} 与
 * {@code /ai/knowledge/embedding/**}，<b>篡改提示词 / 模型配置 / 知识库，或触发全量向量化</b>。
 * 这比"管理员无分级"更严重：后者至少要求先登录。</p>
 *
 * <p>现按与用户端 {@link UserAuthInterceptor} 完全一致的四步校验：</p>
 * <ol>
 *   <li>{@code token} 头必须以 {@code "5grcs "} 开头（api 模块 TokenUtil 的格式）；</li>
 *   <li>解析 JWT payload（Base64URL 解填充 → Jackson），取 {@code token_userid} / {@code exp} / {@code token_role}；</li>
 *   <li>校验未过期，且 {@code token_role} 必须为 {@code admin}（用户 token 不得访问管理端）；</li>
 *   <li>Redis 权威比对 {@code login:token:admin:<id>} 与请求头 token <b>完全一致</b>——
 *       因比的是整串，篡改 payload 或伪造签名都无法通过，无需再单独验签。</li>
 * </ol>
 *
 * <p>随后按 {@code token_admin_role} 做最小权限判定：
 * <b>{@code /ai/admin/**} 与 {@code /ai/knowledge/embedding/**} 的所有"写"操作
 * （非 GET / HEAD / OPTIONS）限 {@code SUPER_ADMIN}</b> —— 这些接口改的是全局提示词、
 * 模型绑定与知识库，影响全体用户，普通运营角色不应具备。只读接口不受此限。</p>
 */
public class AdminAuthInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AdminAuthInterceptor.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 与 api 模块 TokenUtil 保持一致 */
    private static final String TOKEN_HEAD = "5grcs ";

    /** 前端使用的登录 token 请求头名 */
    private static final String TOKEN_HEADER = "token";

    private final LoginTokenStore tokenStore;

    public AdminAuthInterceptor(LoginTokenStore tokenStore) {
        this.tokenStore = tokenStore;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        // 放行 CORS 预检请求，避免 OPTIONS 被拦截
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String token = request.getHeader(TOKEN_HEADER);
        if (token == null || !token.startsWith(TOKEN_HEAD)) {
            log.warn("管理接口拒绝（缺少合法token） [{} {}]", request.getMethod(), request.getRequestURI());
            return reject(response, 401, "未认证，请先登录");
        }

        JsonNode claims = parseClaims(token.substring(TOKEN_HEAD.length()));
        if (claims == null) {
            log.warn("管理token解析失败 [{} {}]", request.getMethod(), request.getRequestURI());
            return reject(response, 401, "登录已过期，请重新登录");
        }

        long adminId = claims.path("token_userid").asLong(0);
        if (adminId <= 0) {
            return reject(response, 401, "token无效");
        }

        // 过期校验（exp 单位：秒）
        long exp = claims.path("exp").asLong(0);
        if (exp > 0 && exp * 1000L < System.currentTimeMillis()) {
            return reject(response, 401, "登录已过期，请重新登录");
        }

        if (!"admin".equals(claims.path("token_role").asText(null))) {
            log.warn("非管理员 token 访问管理接口 [{} {}]", request.getMethod(), request.getRequestURI());
            return reject(response, 403, "无权限访问后台接口");
        }

        // Redis 权威比对：登出/被顶号后立即失效
        if (!tokenStore.isAdminTokenValid(adminId, token)) {
            return reject(response, 401, "token已过期或已注销，请重新登录");
        }

        String adminRole = AdminContext.normalize(claims.path("token_admin_role").asText(null));
        AdminContext.setAdminId(adminId);
        AdminContext.setAdminRole(adminRole);

        // 最小权限：管理端的"写"操作限超级管理员
        if (isWriteMethod(request.getMethod())
                && !AdminContext.ROLE_SUPER_ADMIN.equals(adminRole)) {
            log.warn("后台权限不足被拒: [{} {}], adminId={}, adminRole={}",
                    request.getMethod(), request.getRequestURI(), adminId, adminRole);
            return reject(response, 403, "该操作会变更全局 AI 配置，仅限超级管理员");
        }

        return true;
    }

    private static boolean isWriteMethod(String method) {
        return !("GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method));
    }

    /**
     * 解析 JWT payload（不校验签名，签名有效性由 Redis 完整 token 比对兜底）。
     * @return claims，格式非法时返回 null
     */
    private JsonNode parseClaims(String jwt) {
        try {
            String[] parts = jwt.split("\\.");
            if (parts.length < 2) {
                return null;
            }
            byte[] payload = Base64.getUrlDecoder().decode(padBase64(parts[1]));
            return MAPPER.readTree(new String(payload, StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.warn("JWT payload 解析异常: {}", e.getMessage());
            return null;
        }
    }

    /** Base64URL 无填充串补齐 padding，避免 Decoder 抛 IllegalArgumentException */
    private String padBase64(String s) {
        int mod = s.length() % 4;
        if (mod == 2) {
            return s + "==";
        }
        if (mod == 3) {
            return s + "=";
        }
        return s;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        AdminContext.clear();
    }

    private boolean reject(HttpServletResponse response, int httpStatus, String msg) throws Exception {
        response.setStatus(httpStatus);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":" + httpStatus + ",\"msg\":\"" + msg + "\",\"data\":null}");
        return false;
    }
}
