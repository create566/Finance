package com.example.demo.interceptor;

import com.example.demo.utils.LoginUserContext;
import com.example.demo.utils.TokenUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wwfinance.common.result.PccAjaxResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;

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

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1. 取 token：先试 Authorization（前台），再试 token 头（后台前端）
        String token = request.getHeader("Authorization");
        if (token == null || token.isEmpty()) {
            token = request.getHeader("token");
        }

        if (token == null || token.isEmpty() || !token.startsWith("5grcs ")) {
            return writeUnauthorized(response, "缺少认证token");
        }

        try {
            Map<String, String> info = TokenUtil.getMapInfoFromToken(token);
            String userid = info.get("token_userid");
            if (userid == null || userid.isEmpty()) {
                return writeUnauthorized(response, "token无效");
            }

            // 写入上下文
            LoginUserContext.setUserid(Integer.parseInt(userid));
            String role = info.get("token_role");
            LoginUserContext.setRole(role);
            LoginUserContext.setUsername(info.get("token_username"));

            // /admin/** 路径要求管理员角色
            String uri = request.getRequestURI();
            if (uri.startsWith("/admin/") && !"admin".equals(role)) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write(MAPPER.writeValueAsString(new PccAjaxResult(403, "无权限访问后台接口", null)));
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
