package com.ausaf.sudoku.service;

import com.ausaf.sudoku.entity.PasswordResetToken;
import com.ausaf.sudoku.entity.User;
import com.ausaf.sudoku.repository.passwordreset.PasswordResetTokenRepository;
import com.ausaf.sudoku.repository.user.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;

/**
 * Issues and redeems single-use password-reset tokens. The raw token only ever exists in
 * memory and in the emailed link - {@link PasswordResetTokenRepository} stores just its SHA-256
 * hash, so a database read alone can't be replayed to reset an account's password.
 */
@Slf4j
@Service
public class PasswordResetService {

    private static final long TOKEN_VALIDITY_MS = 30L * 60 * 1000; // 30 minutes

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordResetTokenRepository tokenRepository;

    @Autowired
    private MailService mailService;

    @Value("${app.base-url}")
    private String appBaseUrl;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Emails a reset link if {@code email} belongs to an account, and quietly does nothing if
     * it doesn't. Callers must show the same message to the end user either way - this
     * method's behavior can't be allowed to reveal which emails are registered.
     */
    public void requestReset(String email) {
        User user = userRepository.findByEmail(email);
        if (user == null) {
            log.info("Password reset requested for an email with no matching account");
            return;
        }

        String rawToken = generateRawToken();
        PasswordResetToken token = new PasswordResetToken(null, user.getId(), hash(rawToken),
                new Date(System.currentTimeMillis() + TOKEN_VALIDITY_MS), false);
        tokenRepository.save(token);

        String resetLink = appBaseUrl + "/reset-password?token=" + rawToken;
        mailService.sendPasswordResetEmail(email, resetLink);
        log.info("Password reset requested for user:{}", user.getId());
    }

    /**
     * Consumes a reset token and sets the account's new password.
     *
     * @throws ResponseStatusException 400 if the token is missing, unknown, already used, or expired
     */
    public void resetPassword(String rawToken, String newPassword) {
        PasswordResetToken token = rawToken == null ? null : tokenRepository.findByTokenHash(hash(rawToken));
        if (token == null || token.isUsed() || token.getExpiresAt().before(new Date())) {
            log.warn("Password reset rejected: invalid, used, or expired token");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired reset link");
        }

        User user = userRepository.findById(token.getUserId()).orElse(null);
        if (user == null) {
            log.warn("Password reset rejected: token's account no longer exists");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired reset link");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        token.setUsed(true);
        tokenRepository.save(token);

        log.info("Password reset completed for user:{}", user.getId());
    }

    private String generateRawToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available on the JVM", e);
        }
    }
}