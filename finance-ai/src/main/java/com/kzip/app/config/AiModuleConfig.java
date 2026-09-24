package com.kzip.app.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class AiModuleConfig {

    private static final Logger log = LoggerFactory.getLogger(AiModuleConfig.class);

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