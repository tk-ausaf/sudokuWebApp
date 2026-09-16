package com.ausaf.sudoku.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

/**
 * A single-use password-reset request. Only a SHA-256 hash of the emailed token is ever stored
 * here, so a database read alone can't be used to reset an account's password. MongoDB's TTL
 * index on {@code expiresAt} (0-second threshold) deletes expired documents automatically -
 * this collection never needs a manual cleanup job.
 */
@Data
@Document(collection = "passwordResetTokens")
@NoArgsConstructor
@AllArgsConstructor
public class PasswordResetToken {

    @Id
    String id;

    String userId;

    /** SHA-256 hex digest of the raw token that was emailed - never the raw token itself. */
    String tokenHash;

    @Indexed(expireAfterSeconds = 0)
    Date expiresAt;

    /** Set once this token has been consumed, so it can't be replayed before it expires. */
    boolean used;
}