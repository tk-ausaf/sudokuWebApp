package com.ausaf.sudoku.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/** Sends transactional emails (currently just password-reset links) via the configured Gmail SMTP relay. */
@Slf4j
@Service
public class MailService {

    @Autowired
    private JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromAddress;

    /**
     * Sends a password-reset link to {@code to}. Failures are logged (this is an unexpected
     * infrastructure problem, not a validation rejection) but never thrown - the caller always
     * shows the user the same generic "check your email" message either way, so a delivery
     * failure can't be used to probe which emails are registered.
     */
    public void sendPasswordResetEmail(String to, String resetLink) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(to);
        message.setSubject("Reset your Sudoku password");
        message.setText(
                "We received a request to reset your Sudoku account password.\n\n"
                + "Choose a new password here:\n" + resetLink + "\n\n"
                + "This link expires in 30 minutes. If you didn't request this, you can safely ignore this email.");
        try {
            mailSender.send(message);
            log.info("Password reset email sent");
        } catch (MailException e) {
            log.error("Failed to send password reset email", e);
        }
    }
}