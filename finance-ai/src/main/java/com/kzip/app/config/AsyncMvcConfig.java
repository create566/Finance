package com.kzip.app.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.lang.NonNull;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * MVC 异步支持配置：为 SSE 流式响应（ReactiveTypeHandler / SseEmitter）提供有界线程池，
 * 替换 Spring MVC 默认的 SimpleAsyncTaskExecutor（每请求新建线程，负载下不适合生产）。
 */
@Configuration
public class AsyncMvcConfig implements WebMvcConfigurer {

    @Bean(name = "sseAsyncExecutor")
    public AsyncTaskExecutor sseAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 核心/最大线程数：SSE 流式是 IO 密集 + 长连接场景，线程数不宜过大
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(64);
        executor.setThreadNamePrefix("sse-async-");
        // 队列满时，由调用线程执行（背压），避免任务无界堆积
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }

    @Override
    public void configureAsyncSupport(@NonNull AsyncSupportConfigurer configurer) {
        // 设定超时与执行器，消除 "Please, configure an AsyncTaskExecutor" 警告
        configurer.setDefaultTimeout(60_000L);
        configurer.setTaskExecutor(sseAsyncExecutor());
    }
}
