package com.example.demo.config;

import com.example.demo.service.strategy.FileStorageStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Slf4j
@Configuration
public class FileStorageConfig {

    @Value("${file.storage.strategy:local}")
    private String storageStrategy;

    @Autowired
    private Map<String, FileStorageStrategy> strategyMap;

    @Bean
    public FileStorageStrategy fileStorageStrategy() {
        FileStorageStrategy strategy = strategyMap.get(storageStrategy + "FileStorage");

        if (strategy == null) {
            log.warn("未找到存储策略: {}, 使用默认的本地存储", storageStrategy);
            strategy = strategyMap.get("localFileStorage");
        }

        if (strategy == null) {
            throw new RuntimeException("无法初始化任何文件存储策略");
        }

        log.info("当前使用的文件存储策略: {} ({})", storageStrategy, strategy.getClass().getSimpleName());
        return strategy;
    }
}