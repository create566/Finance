package com.wwfinance.oss.config;

import com.wwfinance.common.auth.JwtAuthInterceptor;
import com.wwfinance.common.auth.JwtAuthInterceptor.AdminPolicy;
import com.wwfinance.common.auth.JwtAuthInterceptor.PathRule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Collections;

/**
 * finance-oss 的 Web 拦截器注册。
 *
 * <p>只作用于本模块自己的文件接口 {@code /api/oss/file/**}，不影响 finance-base
 * 通过组件扫描带进来的其它 WebMvc 配置。</p>
 *
 * <p>放行项只有两类：</p>
 * <ul>
 *   <li>{@code /api/oss/file/hello} —— 存活探针，用于确认服务已被网关正确路由；</li>
 *   <li>{@code /api/oss/file/images/**} —— 已经落盘的图片本身。<b>与 api 模块的处理保持一致</b>：
 *       读文件靠「文件名不可猜」，不需要登录态；而写（上传）与删（remove）必须登录。</li>
 * </ul>
 *
 * <p><b>2026-10-08</b>：{@code JwtAuthInterceptor} 已上移到 finance-common（主链共享），
 * 本模块只在此声明自己的「角色-路径准入策略」。</p>
 */
@Configuration
public class OssWebConfig implements WebMvcConfigurer {

    /**
     * oss 的准入策略：本模块只提供前台用户接口，管理员 token 一律拒绝。
     *
     * <p><b>与迁移前逐条等价</b>：原先 oss 版拦截器是「只要 role=admin 就 403」，而本拦截器
     * 只注册在 {@code /api/oss/file/**} 上 —— 所有会被它拦到的请求都以 {@code /api/oss/file} 开头，
     * 因此「前缀匹配 + 禁止 admin」与原「无条件禁止 admin」的判定结果完全一致。</p>
     */
    @Bean
    public JwtAuthInterceptor jwtAuthInterceptor(StringRedisTemplate stringRedisTemplate) {
        return new JwtAuthInterceptor(stringRedisTemplate, Collections.singletonList(
                new PathRule("/api/oss/file", AdminPolicy.FORBIDDEN, "管理员请使用后台管理接口")));
    }

    @Autowired
    private JwtAuthInterceptor jwtAuthInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtAuthInterceptor)
                .addPathPatterns("/api/oss/file/**")
                .excludePathPatterns(
                        "/api/oss/file/hello",
                        "/api/oss/file/images/**"
                );
    }
}
