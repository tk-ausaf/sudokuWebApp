package com.ausaf.sudoku.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;

/**
 * Signs and verifies this app's real-user JWTs (subject = username), using an HMAC signing key.
 * Guest sessions are identified separately and don't use JWTs at all - see
 * {@link GuestSessionFilter}.
 */
@Slf4j
@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String jwtSecret;

    private final long jwtExpirationMs = 86400000; // 24 hours

    /** Derives the HMAC-SHA signing key from the configured {@code jwt.secret}. */
    private Key getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes());
    }

    /** Issues a 24-hour real-user token whose subject is the username. */
    public String generateToken(String username) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpirationMs);

        return Jwts.builder()
                .setSubject(username)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    /** @return the subject (username) of a real-user token - callers should have validated it first. */
    public String getUsernameFromToken(String token) {
        return parseClaims(token).getSubject();
    }

    /** True if the token is well-formed and unexpired - every token this app issues now is a real-user token. */
    public boolean isUserToken(String token) {
        return validateToken(token);
    }

    /**
     * Extracts the token from a request's {@code Authorization: Bearer <jwt>} header.
     * @return the token, or null if the header is absent or not a bearer token.
     */
    public String extractBearerToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }

    /** @return true if the request carries a bearer token that is valid and belongs to a real user (not a guest). */
    public boolean isRealUserRequest(HttpServletRequest request) {
        String token = extractBearerToken(request);
        return token != null && isUserToken(token);
    }

    /** @return true if the token's signature and expiry both check out (guest or real-user). */
    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            // Routine (expired session, logged-out client retrying a stale token) - never the
            // token itself, and DEBUG rather than WARN since this isn't an anomaly.
            log.debug("Token validation failed: {}", e.toString());
            return false;
        }
    }

    /** @throws JwtException if the token's signature is invalid, malformed, or expired. */
    private Claims parseClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
