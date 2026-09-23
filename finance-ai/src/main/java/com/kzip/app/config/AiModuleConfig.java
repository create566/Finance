package com.kzip.app.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Configuration
public class AiModuleConfig {

    @Value("${api.module.callback-url:http://localhost:8990/admin/core/knowledge}")
    private String apiCallbackUrl;

    @Value("${api.module.health-url:http://localhost:8990/admin/core/knowledge/health}")
    private String apiHealthUrl;

    @Bean
    public RestTemplate restTemplate() {
        log.info("初始化RestTemplate Bean");
        log.info("API回调地址: {}", apiCallbackUrl);
        log.info("API健康检查地址: {}", apiHealthUrl);
        return new RestTemplate();
    }
}