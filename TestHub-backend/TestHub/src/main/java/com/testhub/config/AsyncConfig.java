package com.testhub.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "taskExecutor")
    @Primary
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);      // threads toujours actifs
        executor.setMaxPoolSize(10);      // max en cas de charge
        executor.setQueueCapacity(50);    // file d'attente
        executor.setThreadNamePrefix("testhub-async-");
        executor.initialize();
        return executor;
    }
}