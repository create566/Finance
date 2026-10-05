package com.kzip.app.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 登录态存取：复用 api 模块写入 Redis 的登录 token
 * （key: login:token:user:{userid}，value: 完整 token）。
 * 用户端鉴权用它实现"登出/被顶号后旧 token 立即失效"。
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
        try {
            String saved = redisTemplate.opsForValue().get("login:token:user:" + userid);
            return token.equals(saved);
        } catch (Exception e) {
            log.error("查询登录态失败(Redis异常,按未登录处理): userid={}, error={}", userid, e.getMessage());
            return false;
        }
    }
}
