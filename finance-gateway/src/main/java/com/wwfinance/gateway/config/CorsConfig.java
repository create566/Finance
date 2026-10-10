package com.wwfinance.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

@Configuration
public class CorsConfig {

    @Bean
    public CorsWebFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true); // 是否允许携带cookie
        // 安全修复：allowCredentials(true) 时不能用通配符 * 作为允许源，
        // 浏览器会直接拒绝 "Access-Control-Allow-Origin: *" + credentials 的组合
        config.addAllowedOrigin("http://localhost:5173");
        config.addAllowedOrigin("http://localhost:5174");
        config.addAllowedOrigin("http://127.0.0.1:5173");
        config.addAllowedOrigin("http://127.0.0.1:5174");

        // ============新增这两行，对应你的公网入口============
        config.addAllowedOrigin("http://120.26.132.5");    // 用户端 :80
        config.addAllowedOrigin("http://120.26.132.5:81"); // 管理端 :81

        config.addAllowedHeader("*"); // 允许携带的头
        config.addAllowedMethod("*"); // 允许访问的方式

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsWebFilter(source);
    }
}