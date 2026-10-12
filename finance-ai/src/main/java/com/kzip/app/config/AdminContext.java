package com.kzip.app.config;

/**
 * 当前登录管理员的线程级上下文（finance-ai 模块）。
 *
 * <p><b>2026-10-09 新增</b>：配合 {@link AdminAuthInterceptor} 的严格鉴权，
 * 保存管理员 id 与细粒度后台角色（{@code SUPER_ADMIN} / {@code ADMIN}），
 * 供需要区分管理员等级的接口使用。</p>
 *
 * <p>取值与 api 模块 {@code com.wwfinance.common.auth.AdminRoles} 保持一致，
 * 但本模块（Spring Boot 3 / jakarta）不依赖 finance-common，故常量在此本地声明。</p>
 */
public final class AdminContext {

    /** 超级管理员：可执行 AI 管理端的全部写操作。 */
    public static final String ROLE_SUPER_ADMIN = "SUPER_ADMIN";

    /** 普通运营：AI 管理端只读。 */
    public static final String ROLE_ADMIN = "ADMIN";

    private static final ThreadLocal<Long> CURRENT_ADMIN_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> CURRENT_ADMIN_ROLE = new ThreadLocal<>();

    public static Long getAdminId() {
        return CURRENT_ADMIN_ID.get();
    }

    public static void setAdminId(Long adminId) {
        CURRENT_ADMIN_ID.set(adminId);
    }

    public static String getAdminRole() {
        return CURRENT_ADMIN_ROLE.get();
    }

    public static void setAdminRole(String role) {
        CURRENT_ADMIN_ROLE.set(role);
    }

    /** 角色归一化：缺失按最小权限 {@link #ROLE_ADMIN} 处理（fail-safe）。 */
    public static String normalize(String role) {
        if (role == null || role.trim().isEmpty()) {
            return ROLE_ADMIN;
        }
        return role.trim().toUpperCase();
    }

    public static void clear() {
        CURRENT_ADMIN_ID.remove();
        CURRENT_ADMIN_ROLE.remove();
    }

    private AdminContext() {
    }
}
