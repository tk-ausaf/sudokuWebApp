package com.ausaf.sudoku.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Sends the user back to the login page with an error flag when a Google sign-in attempt fails.
 * Redirects to {@code app.base-url} (the frontend's own origin, not this backend's) rather than a
 * relative path, since the two are separate deployed services.
 */
@Slf4j
@Component
public class GoogleOAuth2FailureHandler implements AuthenticationFailureHandler {

    @Value("${app.base-url}")
    private String appBaseUrl;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) throws IOException {
        log.warn("Google login failed: {}", exception.getMessage());
        response.sendRedirect(appBaseUrl + "/login?error=google_auth_failed");
    }
}