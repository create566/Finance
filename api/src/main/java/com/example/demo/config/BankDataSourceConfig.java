package com.example.demo.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Slf4j
@Configuration
public class BankDataSourceConfig {

    @Value("${bank-datasource.url}")
    private String url;

    @Value("${bank-datasource.username}")
    private String username;

    @Value("${bank-datasource.password}")
    private String password;

    @Value("${bank-datasource.driver-class-name}")
    private String driverClassName;

    @Value("${bank-datasource.connect-timeout-ms:8000}")
    private long connectTimeoutMs;

    @Bean("bankJdbcTemplate")
    public JdbcTemplate bankJdbcTemplate() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName(driverClassName);
        config.setPoolName("BankHikariPool");
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(connectTimeoutMs);
        config.setValidationTimeout(3000);
        // 银行库是只读旁路镜像，网络抖动时不应阻断平台自身启动：
        // <0 表示启动时不预建连接(checkFailFast 直接返回)，失败由首次业务调用承担，
        // BankAccountSyncServiceImpl 已兜底降级到平台 user_account。
        config.setInitializationFailTimeout(-1);
        DataSource ds = new HikariDataSource(config);
        log.info("银行JdbcTemplate初始化完成（延迟建连）: {}", url);
        return new JdbcTemplate(ds);
    }
}