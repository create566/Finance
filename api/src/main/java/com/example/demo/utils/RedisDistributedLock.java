package com.example.demo.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 基于 Redis 的简易分布式锁
 *
 * <p>项目未引入 Redisson / ShedLock，这里用 {@code SET key value NX EX} + Lua 释放脚本实现，
 * 只覆盖定时任务「同一时刻只允许一个实例执行」这一最小诉求。</p>
 *
 * <p><b>三个必须守住的点：</b></p>
 * <ol>
 *   <li><b>加锁必须带过期时间</b> —— 实例加锁后宕机不会导致锁永久滞留（死锁）。
 *       过期时间要大于任务最大执行时长，否则会提前失效、两个实例同时跑。</li>
 *   <li><b>释放必须校验持有者 token</b> —— 直接 {@code DEL} 会删掉别的实例持有的锁。
 *       本类用 Lua 脚本做「比对 value 再删」的原子操作（GET 和 DEL 非原子，分开写有竞态）。</li>
 *   <li><b>不支持可重入、不支持自动续期</b> —— 任务执行超过 expireSeconds 后锁失效，
 *       调用方需自行保证任务时长可控。</li>
 * </ol>
 */
@Slf4j
@Component
public class RedisDistributedLock {

    private static final String LOCK_KEY_PREFIX = "job:lock:";

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private final DefaultRedisScript<Long> unlockScript = new DefaultRedisScript<>();

    public RedisDistributedLock() {
        unlockScript.setResultType(Long.class);
        unlockScript.setScriptSource(
                new ResourceScriptSource(new ClassPathResource("lua/unlock.lua")));
    }

    /**
     * 尝试加锁
     *
     * @param lockName      锁名（业务唯一，如 overdueCheck）
     * @param expireSeconds 锁过期时间（秒），必须大于任务最大执行时长
     * @return 加锁成功返回 token（释放时要用），失败返回 null
     */
    public String tryLock(String lockName, long expireSeconds) {
        String key = LOCK_KEY_PREFIX + lockName;
        String token = UUID.randomUUID().toString();
        try {
            Boolean ok = stringRedisTemplate.opsForValue()
                    .setIfAbsent(key, token, expireSeconds, TimeUnit.SECONDS);
            if (Boolean.TRUE.equals(ok)) {
                log.info("分布式锁获取成功：lockName={}, expire={}s", lockName, expireSeconds);
                return token;
            }
            log.info("分布式锁获取失败，已有实例在执行：lockName={}", lockName);
            return null;
        } catch (Exception e) {
            // Redis 不可达时不能静默放行：定时任务多实例重复执行会重复动钱。
            // 抛出去让调用方决定（本项目的策略是跳过本次执行并告警）。
            log.error("分布式锁获取异常，Redis 可能不可达：lockName={}", lockName, e);
            throw new IllegalStateException("获取分布式锁失败：" + lockName, e);
        }
    }

    /**
     * 释放锁（仅当 value 仍是自己的 token 时才删）
     *
     * @param lockName 锁名
     * @param token    {@link #tryLock} 返回的 token
     */
    public void unlock(String lockName, String token) {
        if (token == null) {
            return;
        }
        String key = LOCK_KEY_PREFIX + lockName;
        try {
            Long result = stringRedisTemplate.execute(
                    unlockScript, Collections.singletonList(key), token);
            if (Long.valueOf(1L).equals(result)) {
                log.info("分布式锁释放成功：lockName={}", lockName);
            } else {
                // 说明锁已过期被别的实例拿走，属于预期内的并发情况，warn 即可
                log.warn("分布式锁释放跳过：锁已过期或不属于当前持有者，lockName={}", lockName);
            }
        } catch (Exception e) {
            log.error("分布式锁释放异常：lockName={}", lockName, e);
        }
    }
}
