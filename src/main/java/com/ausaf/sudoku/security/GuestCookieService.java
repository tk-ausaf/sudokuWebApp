package com.ausaf.sudoku.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Issues/reads the httpOnly guest-session cookie. The cookie's token is only ever trusted
 * when read directly from the request by this class (never from a client-supplied body field),
 * so ownership can't be spoofed by claiming another guest's anonymous id.
 */
@Component
public class GuestCookieService {

    public static final String COOKIE_NAME = "sudoku_guest";
    public static final String REQUEST_ATTR = "anonymousId";

    private static final long GUEST_COOKIE_MAX_AGE_SECONDS = 90L * 24 * 60 * 60;

    @Autowired
    private JwtUtil jwtUtil;

    @Value("${cookie.secure:false}")
    private boolean secureCookie;

    public String extractGuestId(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (COOKIE_NAME.equals(cookie.getName())) {
                String token = cookie.getValue();
                if (jwtUtil.validateToken(token) && jwtUtil.isGuestToken(token)) {
                    return jwtUtil.getSubject(token);
                }
            }
        }
        return null;
    }

    public void issueGuestCookie(HttpServletResponse response, String anonymousId) {
        String token = jwtUtil.generateGuestToken(anonymousId);
        setCookieHeader(response, token, GUEST_COOKIE_MAX_AGE_SECONDS);
    }

    public void clearGuestCookie(HttpServletResponse response) {
        setCookieHeader(response, "", 0);
    }

    private void setCookieHeader(HttpServletResponse response, String value, long maxAgeSeconds) {
        String header = String.format(
                "%s=%s; Max-Age=%d; Path=/; HttpOnly; SameSite=Lax%s",
                COOKIE_NAME, value, maxAgeSeconds, secureCookie ? "; Secure" : "");
        response.addHeader("Set-Cookie", header);
    }
}