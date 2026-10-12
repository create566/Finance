package com.wwfinance.common.utils;

/**
 * 当前登录用户上下文（线程级）。
 *
 * <p>拦截器在 preHandle 中写入，Controller/Service 在业务中读取，afterCompletion 中清除。</p>
 *
 * <p><b>2026-10-08 抽取到 finance-common</b>：此前 api（{@code com.example.demo.utils}）与
 * finance-oss（{@code com.wwfinance.oss.utils}）各有一份完全相同的实现，属于纯复制。
 * 现在两模块共用本类，语义单点维护 —— 任何字段/方法调整不会再出现「改一边漏一边」。</p>
 */
public class LoginUserContext {

    private static final ThreadLocal<Integer> CURRENT_USERID = new ThreadLocal<>();
    private static final ThreadLocal<String> CURRENT_ROLE = new ThreadLocal<>();
    private static final ThreadLocal<String> CURRENT_USERNAME = new ThreadLocal<>();
    /**
     * 细粒度后台角色（SUPER_ADMIN / ADMIN），2026-10-09 P2-11 新增。
     *
     * <p>与 {@link #getRole()} 的区别：{@code role} 只有 {@code admin} / {@code null} 两种取值，
     * 是"前台还是后台"的判别位；本字段才是后台内部的分级角色。</p>
     */
    private static final ThreadLocal<String> CURRENT_ADMIN_ROLE = new ThreadLocal<>();

    public static Integer getUserid() {
        return CURRENT_USERID.get();
    }

    public static void setUserid(Integer userid) {
        CURRENT_USERID.set(userid);
    }

    public static String getRole() {
        return CURRENT_ROLE.get();
    }

    public static void setRole(String role) {
        CURRENT_ROLE.set(role);
    }

    public static String getUsername() {
        return CURRENT_USERNAME.get();
    }

    public static void setUsername(String username) {
        CURRENT_USERNAME.set(username);
    }

    public static boolean isAdmin() {
        return "admin".equals(CURRENT_ROLE.get());
    }

    /**
     * 当前后台账号的细粒度角色（SUPER_ADMIN / ADMIN）。
     *
     * <p>非管理员请求返回 {@code null}。注意：<b>不要</b>把它当作布尔用 ——
     * 取值缺失时应按最小权限处理（见 {@code AdminRoles.normalize}）。</p>
     */
    public static String getAdminRole() {
        return CURRENT_ADMIN_ROLE.get();
    }

    public static void setAdminRole(String adminRole) {
        CURRENT_ADMIN_ROLE.set(adminRole);
    }

    /** 当前请求是否为超级管理员（后台内部最高权限）。 */
    public static boolean isSuperAdmin() {
        return com.wwfinance.common.auth.AdminRoles.SUPER_ADMIN
                .equals(CURRENT_ADMIN_ROLE.get());
    }

    public static void clear() {
        CURRENT_USERID.remove();
        CURRENT_ROLE.remove();
        CURRENT_USERNAME.remove();
        CURRENT_ADMIN_ROLE.remove();
    }
}
