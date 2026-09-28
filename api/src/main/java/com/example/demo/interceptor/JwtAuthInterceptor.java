package com.example.demo.interceptor;

import com.example.demo.utils.LoginUserContext;
import com.example.demo.utils.TokenUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wwfinance.common.result.PccAjaxResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 统一 JWT 授权拦截器
 * 1. 从 Authorization 头（前台用户）或 token 头（后台管理员）取 token
 * 2. 解析 token 拿到用户ID/角色，写入 LoginUserContext
 * 3. /admin/** 路径要求 token_role=admin，否则 403
 * 4. 解析失败返回 401 JSON
 */
@Slf4j
@Component
public class JwtAuthInterceptor implements HandlerInterceptor {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final long TOKEN_TTL = 604800;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1. 取 token：先试 Authorization（前台），再试 token 头（后台前端）
        String token = request.getHeader("Authorization");
        if (token == null || token.isEmpty()) {
            token = request.getHeader("token");
        }

        log.info("JWT拦截器: uri={}, Authorization头={}, token头={}", request.getRequestURI(), request.getHeader("Authorization"), request.getHeader("token"));

        if (token == null || token.isEmpty() || !token.startsWith("5grcs ")) {
            log.warn("JWT拦截器拒绝: token={}", token);
            return writeUnauthorized(response, "缺少认证token");
        }

        try {
            Map<String, String> info = TokenUtil.getMapInfoFromToken(token);
            String userid = info.get("token_userid");
            if (userid == null || userid.isEmpty()) {
                return writeUnauthorized(response, "token无效");
             }

            // Redis key 按角色命名空间隔离：user 表与 sys_admin 表主键可能相同
            String role = info.get("token_role");
            String redisKey = "login:token:" + ("admin".equals(role) ? "admin:" : "user:") + userid;
            String redisToken = stringRedisTemplate.opsForValue().get(redisKey);
            if (redisToken == null) {
                return writeUnauthorized(response, "token已过期或已注销");
            }
            if (!token.equals(redisToken)) {
                return writeUnauthorized(response, "token无效，请重新登录");
            }

            stringRedisTemplate.expire(redisKey, TOKEN_TTL, TimeUnit.SECONDS);

            LoginUserContext.setUserid(Integer.parseInt(userid));
            LoginUserContext.setRole(role);
            LoginUserContext.setUsername(info.get("token_username"));

            String uri = request.getRequestURI();

            if (uri.startsWith("/admin/") && !"admin".equals(role)) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write(MAPPER.writeValueAsString(new PccAjaxResult(403, "无权限访问后台接口", null)));
                return false;
            }

            if (uri.startsWith("/api/") && "admin".equals(role)) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write(MAPPER.writeValueAsString(new PccAjaxResult(403, "管理员请使用后台管理接口", null)));
                return false;
            }

            return true;
        } catch (Exception e) {
            log.warn("token解析失败: {}", e.getMessage());
            return writeUnauthorized(response, "token解析失败");
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        LoginUserContext.clear();
    }

    private boolean writeUnauthorized(HttpServletResponse response, String msg) throws Exception {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(MAPPER.writeValueAsString(new PccAjaxResult(401, msg, null)));
        return false;
    }
}