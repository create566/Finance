package com.example.demo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 短信通知专用线程池。
 *
 * <p>有界队列 + CallerRunsPolicy：队列打满时回退到调用线程执行，形成背压，
 * 既不会无限堆积任务，也不会静默丢消息。
 */
@Configuration
public class AsyncConfig {

    @Bean("smsExecutor")
    public ThreadPoolTaskExecutor smsExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(200);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("msg-sms-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
