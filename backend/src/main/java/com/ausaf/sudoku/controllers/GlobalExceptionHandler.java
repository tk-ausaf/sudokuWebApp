package com.ausaf.sudoku.controllers;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Catch-all error handling for every controller. A {@link ResponseStatusException} is a
 * deliberate, already-logged rejection (each throw site logs its own WARN) - this only translates
 * its reason into a JSON body the frontend can read, since Spring's default error body hides the
 * reason unless {@code server.error.include-message} is explicitly enabled. A
 * {@link NoResourceFoundException} is likewise routine, not a bug - see its handler below.
 * Anything else reaching here is unexpected, so it's logged at ERROR with its stack trace (per
 * this app's logging conventions) and never exposed to the client beyond a generic message.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** Renders a deliberate {@link ResponseStatusException}'s status and reason as a JSON body. */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatus(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode()).body(errorBody(ex.getStatusCode().value(), ex.getReason()));
    }

    /**
     * A GET for a path with no controller mapping and no static resource - routine now that this
     * backend no longer embeds a frontend build (the frontend is a separate Vercel deployment), so
     * hitting its bare root ({@code /}) or any other stray path is expected, not a bug. DEBUG-only
     * so it doesn't spam production logs (e.g. from a platform health check still probing {@code /}
     * instead of {@code /health}), but still visible with {@code LOG_LEVEL=DEBUG} if needed.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNoResource(NoResourceFoundException ex) {
        log.debug("No resource or mapping for '{}'", ex.getResourcePath());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorBody(HttpStatus.NOT_FOUND.value(), "Not found"));
    }

    /** Logs an unexpected exception with its stack trace and returns a generic 500 - no internal detail is exposed. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(errorBody(HttpStatus.INTERNAL_SERVER_ERROR.value(), "An unexpected error occurred"));
    }

    private Map<String, Object> errorBody(int status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status);
        body.put("message", message);
        return body;
    }
}