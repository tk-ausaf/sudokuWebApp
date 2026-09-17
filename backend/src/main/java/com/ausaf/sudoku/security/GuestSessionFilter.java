package com.ausaf.sudoku.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Runs before {@link JwtAuthenticationFilter}. If the request doesn't carry a valid real-user
 * bearer token, trusts whatever anonymous id the client sends in the {@value #GUEST_ID_HEADER}
 * header - generated and persisted client-side (browser localStorage), never minted or verified
 * server-side - and stashes it as a request attribute for guest-allowed endpoints to use. This is
 * a deliberate trade-off: unlike the old signed guest cookie, a client-supplied id is technically
 * spoofable, but it works over any HTTP transport (a proxied same-origin call, a direct
 * cross-origin one) without depending on browser cookie policy - which a signed cookie could not
 * guarantee once the frontend and backend became separate deployed services. The blast radius of
 * spoofing is low: no credentials or PII are gated by this id, only which anonymous puzzle
 * attempts/games a browser sees as "its own". Guests are never pushed into the SecurityContext -
 * they remain unauthenticated at the Spring Security layer; identity resolution for guests
 * happens at the application layer.
 */
@Slf4j
@Component
public class GuestSessionFilter extends OncePerRequestFilter {

    public static final String REQUEST_ATTR = "anonymousId";
    public static final String GUEST_ID_HEADER = "X-Guest-Id";

    @Autowired
    private JwtUtil jwtUtil;

    /** Passes through untouched if a valid real-user token is present; otherwise stashes the caller's client-supplied guest id, if any. */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        if (!jwtUtil.isRealUserRequest(request)) {
            String anonymousId = request.getHeader(GUEST_ID_HEADER);
            if (anonymousId != null && !anonymousId.isBlank()) {
                request.setAttribute(REQUEST_ATTR, anonymousId);
                log.debug("Resolved guest:{} from {} header", anonymousId, GUEST_ID_HEADER);
            }
        }

        filterChain.doFilter(request, response);
    }
}