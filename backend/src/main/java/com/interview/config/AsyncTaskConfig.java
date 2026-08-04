package com.interview.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class AsyncTaskConfig {

    @Bean("mentorTaskExecutor")
    public ThreadPoolTaskExecutor mentorTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("mentor-cache-");
        executor.initialize();
        return executor;
    }

    @Bean("appJobTaskExecutor")
    public ThreadPoolTaskExecutor appJobTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("app-job-");
        executor.initialize();
        return executor;
    }

    @Bean("questionBankSyncTaskExecutor")
    public ThreadPoolTaskExecutor questionBankSyncTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(10);
        executor.setThreadNamePrefix("question-bank-sync-");
        executor.initialize();
        return executor;
    }

    @Bean("interviewAgentTaskExecutor")
    public ThreadPoolTaskExecutor interviewAgentTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(32);
        executor.setThreadNamePrefix("interview-agent-");
        executor.initialize();
        return executor;
    }
}
