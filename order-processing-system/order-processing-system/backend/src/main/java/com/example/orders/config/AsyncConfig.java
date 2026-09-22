package com.example.orders.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Bounded thread pool used to process orders concurrently.
 *
 * corePoolSize -> baseline concurrent order workers
 * maxPoolSize  -> burst capacity under load
 * queueCapacity -> backlog before new submissions start blocking the caller
 *
 * CallerRunsPolicy means that if the pool AND the queue are both full,
 * the submitting thread (the REST controller thread) processes the order
 * itself instead of the task being silently dropped or an exception being
 * thrown to the client. This gives natural back-pressure.
 */
@Configuration
public class AsyncConfig {

    @Bean(name = "orderProcessingExecutor")
    public ThreadPoolTaskExecutor orderProcessingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("order-worker-");
        RejectedExecutionHandler backPressureHandler = new ThreadPoolExecutor.CallerRunsPolicy();
        executor.setRejectedExecutionHandler(backPressureHandler);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
