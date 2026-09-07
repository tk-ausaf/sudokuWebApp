package com.ausaf.sudoku.controllers;

import com.ausaf.sudoku.config.GuestHandshakeInterceptor;
import com.ausaf.sudoku.security.CallerIdentity;
import com.ausaf.sudoku.security.GuestCookieService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Resolves the {@link CallerIdentity} behind an incoming request - the authenticated user from
 * {@link SecurityContextHolder} if present, else a guest id from wherever the transport stashed
 * it. Shared by every HTTP controller and by {@link MultiplayerMoveController}'s STOMP handler,
 * which previously each duplicated this same fallback logic.
 */
@Component
public class CallerIdentityExtractor {

    /** Resolves identity for an HTTP request from the guest cookie's request attribute {@link GuestCookieService} sets. */
    public CallerIdentity fromHttpRequest(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            return CallerIdentity.ofUser(auth.getName());
        }
        Object anonymousId = request.getAttribute(GuestCookieService.REQUEST_ATTR);
        return CallerIdentity.ofGuest(anonymousId != null ? anonymousId.toString() : null);
    }

    /** Resolves identity for a STOMP message from the session attributes {@link GuestHandshakeInterceptor} stashed at handshake time. */
    public CallerIdentity fromWebSocketSession(Map<String, Object> sessionAttributes) {
        if (sessionAttributes != null) {
            Object username = sessionAttributes.get(GuestHandshakeInterceptor.SESSION_ATTR_USERNAME);
            if (username != null) {
                return CallerIdentity.ofUser(username.toString());
            }
            Object anonymousId = sessionAttributes.get(GuestHandshakeInterceptor.SESSION_ATTR_ANONYMOUS_ID);
            if (anonymousId != null) {
                return CallerIdentity.ofGuest(anonymousId.toString());
            }
        }
        return CallerIdentity.ofGuest(null);
    }
}