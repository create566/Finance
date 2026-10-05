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
 * 用户端接口鉴权拦截器（修复"会话数据全用户共享"问题）。
 *
 * 校验链路与 api 模块登录态保持一致：
 * 1. token 头必须以 "5grcs " 开头（api 模块 TokenUtil 格式）；
 * 2. 解析 JWT payload 取 token_userid / exp / token_role（Base64URL + Jackson，
 *    不引入 jjwt —— api 用 jjwt 0.9.1 生成的是 Base64URL 无填充串，
 *    新版 jjwt 严格 Base64 解不开，自研解析反而无版本耦合）；
 * 3. Redis 权威校验：login:token:user:{id} 必须与请求头 token 完全一致 ——
 *    登出/被顶号后旧 token 立即失效，同时因为比对的是完整 token 串，
 *    篡改 payload 伪装他人身份的请求必然不匹配，无需再做签名验证；
 * 4. 管理员 token（token_role=admin）不允许访问用户端接口。
 *
 * 通过后 UserContext 持有真实 userid，Controller 一律以它为准，
 * 不再信任前端自报的 userId（此前 userId=1 写死导致所有用户共享会话/记忆）。
 */
public class UserAuthInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(UserAuthInterceptor.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 与 api 模块 TokenUtil 保持一致 */
    private static final String TOKEN_HEAD = "5grcs ";

    private final LoginTokenStore tokenStore;

    public UserAuthInterceptor(LoginTokenStore tokenStore) {
        this.tokenStore = tokenStore;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String token = request.getHeader("token");
        if (token == null || !token.startsWith(TOKEN_HEAD)) {
            return reject(response, "请先登录");
        }

        JsonNode claims = parseClaims(token.substring(TOKEN_HEAD.length()));
        if (claims == null) {
            log.warn("用户token解析失败 [{} {}]", request.getMethod(), request.getRequestURI());
            return reject(response, "登录已过期，请重新登录");
        }

        Long userid = claims.path("token_userid").isMissingNode()
                ? null : claims.path("token_userid").asLong(0);
        if (userid == null || userid <= 0) {
            return reject(response, "token无效");
        }

        // 过期校验（exp 单位：秒）
        long exp = claims.path("exp").asLong(0);
        if (exp > 0 && exp * 1000L < System.currentTimeMillis()) {
            return reject(response, "登录已过期，请重新登录");
        }

        if ("admin".equals(claims.path("token_role").asText(null))) {
            return reject(response, "管理员请使用后台管理接口");
        }

        if (!tokenStore.isUserTokenValid(userid, token)) {
            return reject(response, "token已过期或已注销，请重新登录");
        }

        UserContext.setUserid(userid);
        return true;
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
        UserContext.clear();
    }

    private boolean reject(HttpServletResponse response, String msg) throws Exception {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":401,\"msg\":\"" + msg + "\",\"data\":null}");
        return false;
    }
}
