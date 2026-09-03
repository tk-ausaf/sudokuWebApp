package com.ausaf.sudoku.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Enables {@code @Async} and provides a small dedicated thread pool for multiplayer move
 * persistence, so a game move's database write never runs on (or blocks) the request/STOMP
 * handling thread.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /** Dedicated pool for {@code MultiplayerGamePersistenceService}, kept separate from Spring's unbounded default executor. */
    @Bean("multiplayerGameExecutor")
    public TaskExecutor multiplayerGameExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("mp-persist-");
        executor.initialize();
        return executor;
    }
}