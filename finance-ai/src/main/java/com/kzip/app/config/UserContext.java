package com.kzip.app.config;

/**
 * 用户端请求上下文（ThreadLocal）。
 * 由 UserAuthInterceptor 在鉴权通过后写入，请求结束后必须 clear（防线程池复用串号）。
 */
public final class UserContext {

    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();

    private UserContext() {
    }

    public static void setUserid(Long userid) {
        USER_ID.set(userid);
    }

    /** @return 当前登录用户 id，未登录时为 null */
    public static Long getUserid() {
        return USER_ID.get();
    }

    public static void clear() {
        USER_ID.remove();
    }
}
