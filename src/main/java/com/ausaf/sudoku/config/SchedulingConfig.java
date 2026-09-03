package com.ausaf.sudoku.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Enables scheduled tasks and provides the dedicated scheduler multiplayer games use to enforce
 * each player's per-turn move deadline.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {

    /** Dedicated scheduler for {@code MultiplayerGameEngine}'s per-turn timeout tasks. */
    @Bean("multiplayerTimeoutScheduler")
    public TaskScheduler multiplayerTimeoutScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(4);
        scheduler.setThreadNamePrefix("mp-timeout-");
        scheduler.initialize();
        return scheduler;
    }
}