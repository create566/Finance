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
        DataSource ds = new HikariDataSource(config);
        log.info("银行JdbcTemplate初始化完成: {}", url);
        return new JdbcTemplate(ds);
    }
}