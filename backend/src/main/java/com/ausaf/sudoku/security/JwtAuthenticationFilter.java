package com.ausaf.sudoku.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Authenticates real users from the {@code Authorization: Bearer <jwt>} header. A missing or
 * invalid token is not rejected here - the request simply proceeds unauthenticated, and it's up
 * to {@code authorizeHttpRequests} rules downstream to decide whether that's allowed.
 */
@Slf4j
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Autowired
    private JwtUtil jwtUtil;

    /**
     * Parses a bearer token if present and, if valid and no authentication is already set,
     * populates the SecurityContext with the username so downstream authorization rules apply.
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        
        String jwtToken = jwtUtil.extractBearerToken(request);
        String username = null;

        if (jwtToken != null) {
            try {
                username = jwtUtil.getUsernameFromToken(jwtToken);
            } catch (Exception e) {
                // Routine (expired/malformed bearer token) rather than an anomaly - never logs
                // the token itself.
                log.debug("Unable to get username from bearer token: {}", e.toString());
            }
        }

        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            if (jwtUtil.validateToken(jwtToken)) {
                UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(username, null, null);
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
                log.debug("Authenticated user '{}' from bearer token", username);
            }
        }

        filterChain.doFilter(request, response);
    }
}
