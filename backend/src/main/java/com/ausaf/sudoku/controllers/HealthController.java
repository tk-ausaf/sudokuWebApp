package com.ausaf.sudoku.controllers;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Trivial liveness check - proves the JVM/Spring context is up, with no identity resolution, DB
 * access, or other dependency involved. Used by the frontend to wake a cold-started backend and
 * to poll for readiness; guest-allowed (see {@link com.ausaf.sudoku.security.SecurityConfig}).
 */
@RestController
public class HealthController {

    @GetMapping("health")
    public String health() {
        return "OK";
    }
}