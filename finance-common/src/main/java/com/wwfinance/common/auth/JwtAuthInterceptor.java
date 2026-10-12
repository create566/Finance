package com.wwfinance.common.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wwfinance.common.result.PccAjaxResult;
import com.wwfinance.common.utils.LoginUserContext;
import com.wwfinance.common.utils.TokenUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 统一 JWT 授权拦截器。
 *
 * <p><b>2026-10-08 抽取到 finance-common</b>：此前 api（{@code com.example.demo.interceptor}）
 * 与 finance-oss（{@code com.wwfinance.oss.interceptor}）各有一份，取 token / 验签 / 比对 Redis /
 * 续期 / 写上下文这条主链完全相同，只有「角色与路径的准入策略」不同。现在主链收敛到本类，
 * 各模块只通过构造函数传入自己的 {@link PathRule} 列表 —— 策略是模块私有的，链路是共享的。</p>
 *
 * <p>校验主链（三点缺一不可，顺序固定）：</p>
 * <ol>
 *   <li>取 {@code Authorization} 头（前台）或 {@code token} 头（后台），必须以 {@code "5grcs "} 开头；</li>
 *   <li>JWT 验签通过并解析出 {@code token_userid}；</li>
 *   <li>与 Redis {@code login:token:<role>:<id>} 的值<b>完全相等</b>（支持注销/顶号），随后续期。</li>
 * </ol>
 *
 * <p><b>2026-10-09（P2-11）追加第 4 步：</b>解析方法/类上的 {@link RequireAdminRole}，
 * 对后台接口做细粒度最小角色判定。详见该类注释里"为什么不用 {@code @PreAuthorize}"的说明。</p>
 */
@Slf4j
public class JwtAuthInterceptor implements HandlerInterceptor {

    /** 角色与路径的准入策略 */
    public enum AdminPolicy {
        /** 命中该路径必须管理员访问，前台用户访问回 403 */
        REQUIRED,
        /** 命中该路径禁止管理员访问（管理员误用前台接口），回 403 */
        FORBIDDEN
    }

    /**
     * 一条路径规则：请求 URI 命中 {@code prefix} 时，按 {@code policy} 判定 token 角色。
     *
     * <p>规则按列表顺序依次判定，命中并违规即返回 403；全部未命中或未违规则放行。</p>
     */
    public static final class PathRule {
        private final String prefix;
        private final AdminPolicy policy;
        private final String message;

        public PathRule(String prefix, AdminPolicy policy, String message) {
            this.prefix = prefix;
            this.policy = policy;
            this.message = message;
        }

        boolean matches(String uri) {
            return uri != null && uri.startsWith(prefix);
        }
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();
    /** token 续期时长：7 天，与签发端保持一致 */
    private static final long TOKEN_TTL = 604800;
    private static final String TOKEN_HEAD = "5grcs ";

    private final StringRedisTemplate stringRedisTemplate;
    private final List<PathRule> rules;
    /**
     * 是否打印每请求的诊断日志。
     *
     * <p>注意：日志里<b>只记 URI 与是否携带 token，绝不写 token 原文</b>——
     * 原 api 版本会把 Authorization 头的完整 token 打进日志，等于把凭证落盘。</p>
     */
    private final boolean logDiagnostics;

    public JwtAuthInterceptor(StringRedisTemplate stringRedisTemplate, List<PathRule> rules) {
        this(stringRedisTemplate, rules, false);
    }

    public JwtAuthInterceptor(StringRedisTemplate stringRedisTemplate, List<PathRule> rules, boolean logDiagnostics) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.rules = rules != null ? rules : Collections.emptyList();
        this.logDiagnostics = logDiagnostics;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1. 取 token：先试 Authorization（前台），再试 token 头（后台前端）
        String token = request.getHeader("Authorization");
        if (token == null || token.isEmpty()) {
            token = request.getHeader("token");
        }

        if (logDiagnostics) {
            log.info("JWT拦截器: uri={}, 是否携带token={}",
                    request.getRequestURI(), token != null && !token.isEmpty());
        }

        if (token == null || token.isEmpty() || !token.startsWith(TOKEN_HEAD)) {
            log.warn("鉴权拒绝（缺少 token）: uri={}", request.getRequestURI());
            return writeJson(response, HttpServletResponse.SC_UNAUTHORIZED, new PccAjaxResult(401, "缺少认证token"));
        }

        try {
            Map<String, String> info = TokenUtil.getMapInfoFromToken(token);
            String userid = info.get("token_userid");
            if (userid == null || userid.isEmpty()) {
                return writeJson(response, HttpServletResponse.SC_UNAUTHORIZED, new PccAjaxResult(401, "token无效"));
            }

            // Redis key 按角色命名空间隔离：user 表与 sys_admin 表主键可能相同
            String role = info.get("token_role");
            String redisKey = "login:token:" + ("admin".equals(role) ? "admin:" : "user:") + userid;
            String redisToken = stringRedisTemplate.opsForValue().get(redisKey);
            if (redisToken == null) {
                return writeJson(response, HttpServletResponse.SC_UNAUTHORIZED,
                        new PccAjaxResult(401, "token已过期或已注销"));
            }
            if (!token.equals(redisToken)) {
                return writeJson(response, HttpServletResponse.SC_UNAUTHORIZED,
                        new PccAjaxResult(401, "token无效，请重新登录"));
            }

            stringRedisTemplate.expire(redisKey, TOKEN_TTL, TimeUnit.SECONDS);

            // 2. 写入上下文，供 Controller/Service 读取
            LoginUserContext.setUserid(Integer.parseInt(userid));
            LoginUserContext.setRole(role);
            LoginUserContext.setUsername(info.get("token_username"));
            LoginUserContext.setAdminRole(AdminRoles.normalize(info.get("token_admin_role")));

            // 3. 按模块传入的规则做角色-路径准入判定（策略在调用方，校验链在本类）
            boolean isAdmin = "admin".equals(role);
            String uri = request.getRequestURI();
            for (PathRule rule : rules) {
                if (!rule.matches(uri)) {
                    continue;
                }
                if (rule.policy == AdminPolicy.REQUIRED && !isAdmin) {
                    return writeJson(response, HttpServletResponse.SC_FORBIDDEN,
                            new PccAjaxResult(403, rule.message));
                }
                if (rule.policy == AdminPolicy.FORBIDDEN && isAdmin) {
                    return writeJson(response, HttpServletResponse.SC_FORBIDDEN,
                            new PccAjaxResult(403, rule.message));
                }
            }

            // 4. 【P2-11 权限分级】方法/类级最小角色校验。
            //    /admin/** 的"是不是管理员"已由上面的 PathRule 保证，这里再判"是哪种管理员"：
            //    放款、下架、发标、作废合同等不可逆动作只放行 SUPER_ADMIN。
            //    注意：不能用 Spring Security 的 @PreAuthorize —— 本项目 SecurityContext 里
            //    没有 Authentication（默认过滤链已关闭），套用会让所有请求直接 500。
            if (isAdmin && handler instanceof HandlerMethod) {
                RequireAdminRole required = resolveRequireAdminRole((HandlerMethod) handler);
                if (required != null && required.value().length > 0) {
                    String adminRole = AdminRoles.normalize(info.get("token_admin_role"));
                    boolean allowed = false;
                    for (String candidate : required.value()) {
                        if (candidate != null && candidate.equalsIgnoreCase(adminRole)) {
                            allowed = true;
                            break;
                        }
                    }
                    if (!allowed) {
                        log.warn("后台权限不足被拒: uri={}, adminRole={}, 需要={}",
                                uri, adminRole, String.join("/", required.value()));
                        return writeJson(response, HttpServletResponse.SC_FORBIDDEN,
                                new PccAjaxResult(403, required.message()));
                    }
                }
            }
            return true;
        } catch (Exception e) {
            log.warn("token解析失败: {}", e.getMessage());
            return writeJson(response, HttpServletResponse.SC_UNAUTHORIZED, new PccAjaxResult(401, "token解析失败"));
        }
    }

    /**
     * 解析方法/类上的 {@link RequireAdminRole}。
     *
     * <p>方法级声明优先于类级 —— 允许"整类开放、个别方法收紧"的写法。</p>
     */
    private RequireAdminRole resolveRequireAdminRole(HandlerMethod handlerMethod) {
        RequireAdminRole onMethod = handlerMethod.getMethodAnnotation(RequireAdminRole.class);
        if (onMethod != null) {
            return onMethod;
        }
        Class<?> beanType = handlerMethod.getBeanType();
        return beanType != null ? beanType.getAnnotation(RequireAdminRole.class) : null;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        LoginUserContext.clear();
    }

    private boolean writeJson(HttpServletResponse response, int httpStatus, PccAjaxResult body) throws Exception {
        response.setStatus(httpStatus);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(MAPPER.writeValueAsString(body));
        return false;
    }
}
