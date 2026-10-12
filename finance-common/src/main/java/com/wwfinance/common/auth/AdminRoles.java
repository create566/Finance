package com.wwfinance.common.auth;

/**
 * 后台管理员角色常量。
 *
 * <p><b>2026-10-09 新增（P2-11 权限分级）</b>：此前 {@code sys_admin} 无角色字段，
 * {@code TokenUtil} 把 {@code token_role} 硬编码成 {@code "admin"}，
 * 结果是<b>任何</b>后台账号都能执行放款、下架、作废合同等全部高危动作。现在引入两级角色：</p>
 *
 * <ul>
 *   <li>{@link #SUPER_ADMIN} —— 超级管理员，可执行放款 / 下架 / 发标 / 作废合同 / 用户启停等高危动作；</li>
 *   <li>{@link #ADMIN} —— 普通运营，只能查看与审核，高危动作被 {@link RequireAdminRole} 拦截。</li>
 * </ul>
 *
 * <p><b>与 {@code token_role} 的区别（务必分清，改错会踢掉全体在线会话）：</b></p>
 * <ul>
 *   <li>{@code token_role} —— 只有 {@code "admin"} / 空 两种取值，是<b>命名空间判别位</b>，
 *       决定 Redis 会话键是 {@code login:token:admin:<id>} 还是 {@code login:token:user:<id>}。
 *       <b>值永远保持 {@code "admin"}，不要改成 SUPER_ADMIN。</b></li>
 *   <li>{@code token_admin_role} —— 本次新增的<b>细粒度角色</b>（SUPER_ADMIN / ADMIN），
 *       只用于权限判定，不参与任何键名计算。</li>
 * </ul>
 */
public final class AdminRoles {

    /** 超级管理员：拥有全部后台权限，含资金类与不可逆动作。 */
    public static final String SUPER_ADMIN = "SUPER_ADMIN";

    /** 普通运营：只读 + 审核；放款 / 下架 / 发标 / 作废合同 / 用户启停等被拒。 */
    public static final String ADMIN = "ADMIN";

    /** 缺失角色时的兜底值：按<b>最小权限</b>处理，宁可少放行也不越权。 */
    public static final String DEFAULT = ADMIN;

    private AdminRoles() {
    }

    /**
     * 归一化：空值回落到 {@link #DEFAULT}（最小权限），并统一转大写。
     *
     * @param role 原始角色串，可为 null
     * @return 一定非空的角色串
     */
    public static String normalize(String role) {
        if (role == null || role.trim().isEmpty()) {
            return DEFAULT;
        }
        return role.trim().toUpperCase();
    }
}
