package com.kzip.app.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MyConfig {
    @Bean
    public String myMessage() {
        return "Hello Bean!  ddd";
    }
}
