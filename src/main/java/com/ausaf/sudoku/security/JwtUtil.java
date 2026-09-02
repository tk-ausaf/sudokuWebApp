package com.ausaf.sudoku.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;

@Component
public class JwtUtil {

    private static final String CLAIM_TYPE = "type";
    private static final String TYPE_GUEST = "guest";

    @Value("${jwt.secret}")
    private String jwtSecret;

    private final long jwtExpirationMs = 86400000; // 24 hours
    private final long guestExpirationMs = 90L * 24 * 60 * 60 * 1000; // 90 days

    private Key getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes());
    }

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

    /** A long-lived token identifying an anonymous guest session, not tied to any account. */
    public String generateGuestToken(String anonymousId) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + guestExpirationMs);

        return Jwts.builder()
                .setSubject(anonymousId)
                .claim(CLAIM_TYPE, TYPE_GUEST)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public String getUsernameFromToken(String token) {
        return parseClaims(token).getSubject();
    }

    /** Subject of a guest token is the anonymous session id, not a username. */
    public String getSubject(String token) {
        return parseClaims(token).getSubject();
    }

    public boolean isGuestToken(String token) {
        try {
            return TYPE_GUEST.equals(parseClaims(token).get(CLAIM_TYPE));
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    /** True if the token is well-formed/unexpired AND is a real user token (not a guest token). */
    public boolean isUserToken(String token) {
        return validateToken(token) && !isGuestToken(token);
    }

    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
