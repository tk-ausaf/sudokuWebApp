package com.ausaf.sudoku.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Runs before {@link JwtAuthenticationFilter}. If the request doesn't carry a valid real-user
 * bearer token, resolves (or mints) an anonymous guest session id from the {@code sudoku_guest}
 * cookie and stashes it as a request attribute for guest-allowed sudoku endpoints to use.
 * Guests are never pushed into the SecurityContext — they remain unauthenticated at the
 * Spring Security layer; identity resolution for guests happens at the application layer.
 */
@Component
public class GuestSessionFilter extends OncePerRequestFilter {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private GuestCookieService guestCookieService;

    /**
     * Passes through untouched if a valid real-user token is present; otherwise resolves or
     * mints a guest session id and stashes it on the request before continuing the chain.
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");
        boolean hasRealUserAuth = authHeader != null
                && authHeader.startsWith("Bearer ")
                && jwtUtil.isUserToken(authHeader.substring(7));

        if (!hasRealUserAuth) {
            String anonymousId = guestCookieService.extractGuestId(request);
            if (anonymousId == null) {
                anonymousId = UUID.randomUUID().toString();
                guestCookieService.issueGuestCookie(response, anonymousId);
            }
            request.setAttribute(GuestCookieService.REQUEST_ATTR, anonymousId);
        }

        filterChain.doFilter(request, response);
    }
}