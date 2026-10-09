package com.kzip.app.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 登录态存取：复用 api 模块写入 Redis 的登录 token
 * （key: login:token:user:{userid} / login:token:admin:{adminId}，value: 完整 token）。
 * 用户端/管理端鉴权用它实现"登出/被顶号后旧 token 立即失效"。
 */
@Component
public class LoginTokenStore {

    private static final Logger log = LoggerFactory.getLogger(LoginTokenStore.class);

    private final StringRedisTemplate redisTemplate;

    public LoginTokenStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 校验该用户当前登录 token 是否与请求携带的一致。
     * Redis 异常时按校验失败处理（fail-closed，与 api 模块 JwtAuthInterceptor 行为一致）。
     */
    public boolean isUserTokenValid(long userid, String token) {
        return isTokenValid("login:token:user:" + userid, token, "userid=" + userid);
    }

    /**
     * 校验该管理员当前登录 token 是否与请求携带的一致。
     *
     * <p><b>2026-10-09 新增</b>：此前管理端拦截器只做"token 非空即放行"，
     * 任意伪造一个 {@code token} 头即可调用 {@code /ai/admin/**} 篡改提示词/模型/知识库。
     * 本方法与用户侧同口径 —— 与 Redis 中 api 写入的完整 token 串比对，
     * 登出/顶号后立即失效，且篡改 payload 也无法通过（比的是整串）。</p>
     *
     * <p>Redis 不可达时 <b>fail-closed</b>（判失败），绝不能"连不上就放行"。</p>
     */
    public boolean isAdminTokenValid(long adminId, String token) {
        return isTokenValid("login:token:admin:" + adminId, token, "adminId=" + adminId);
    }

    private boolean isTokenValid(String redisKey, String token, String who) {
        try {
            String saved = redisTemplate.opsForValue().get(redisKey);
            return token.equals(saved);
        } catch (Exception e) {
            log.error("查询登录态失败(Redis异常,按未登录处理): {}, error={}", who, e.getMessage());
            return false;
        }
    }
}
