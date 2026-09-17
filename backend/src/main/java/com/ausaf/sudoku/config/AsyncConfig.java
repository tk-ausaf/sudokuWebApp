package com.ausaf.sudoku.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Arrays;

/**
 * Enables {@code @Async} and provides a small dedicated thread pool for multiplayer move
 * persistence, so a game move's database write never runs on (or blocks) the request/STOMP
 * handling thread.
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

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

    /**
     * An {@code @Async void} method's exception has nowhere to propagate to - without this,
     * Spring's default handler only logs it at WARN with no context about which call failed.
     * Logs at ERROR with the method and arguments so a swallowed persistence failure is never
     * silently lost.
     */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (ex, method, params) -> log.error("Async method {} threw with args {}",
                method.getName(), Arrays.toString(params), ex);
    }
}