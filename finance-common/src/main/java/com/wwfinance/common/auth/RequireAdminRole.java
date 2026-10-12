package com.wwfinance.common.auth;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 后台接口的最小角色要求。
 *
 * <p><b>2026-10-09 新增（P2-11 权限分级）</b>。用于给放款、下架、发标、作废合同、
 * 用户启停等<b>高危或不可逆</b>的后台接口加最小权限约束。</p>
 *
 * <p><b>为什么不用 Spring Security 的 {@code @PreAuthorize}：</b>
 * 本项目的鉴权主链是自定义的 {@link JwtAuthInterceptor} + {@code LoginUserContext}（ThreadLocal），
 * {@code SecurityConfig} 里 {@code http.authorizeRequests().anyRequest().permitAll()}
 * 关闭了默认过滤链，<b>{@code SecurityContextHolder} 中没有任何 Authentication</b>。
 * 此时套用 {@code @PreAuthorize("hasRole('ADMIN')")} 会对<b>每一个</b>请求抛
 * {@code AuthenticationCredentialsNotFoundException} —— 不是"更严格"，是"全站 500"。
 * 因此这里沿用项目既有的"拦截器 + 显式注解"风格，与本项目的 {@code BalanceGuard} 一脉相承。</p>
 *
 * <p><b>用法：</b></p>
 * <pre>{@code
 * @RequireAdminRole(AdminRoles.SUPER_ADMIN)          // 只允许超级管理员
 * @PutMapping("/{id}/makeLoan")
 * public PccAjaxResult makeLoan(@PathVariable Long id) { ... }
 *
 * @RequireAdminRole                                  // 不写 value = 任意后台角色均可（仅作显式声明）
 * @GetMapping("/page")
 * }</pre>
 *
 * <p>注解可加在<b>方法</b>或<b>类</b>上；方法上的声明优先于类上的。</p>
 */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireAdminRole {

    /**
     * 允许访问的角色白名单，取值见 {@link AdminRoles}。
     *
     * <p>留空表示"不限制具体角色"（但调用方仍必须是管理员 —— {@code /admin/**}
     * 的准入由 {@code JwtAuthInterceptor} 的 {@code PathRule} 统一保证）。</p>
     */
    String[] value() default {};

    /** 拒绝时的提示文案。默认给出可解释的原因，而不是笼统的"无权限"。 */
    String message() default "当前账号权限不足，该操作仅限超级管理员";
}
