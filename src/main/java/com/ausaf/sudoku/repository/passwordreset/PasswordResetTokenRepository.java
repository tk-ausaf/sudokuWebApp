package com.ausaf.sudoku.repository.passwordreset;

import com.ausaf.sudoku.entity.PasswordResetToken;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/** Spring Data MongoDB repository for {@link PasswordResetToken}s. */
@Repository
public interface PasswordResetTokenRepository extends MongoRepository<PasswordResetToken, String> {
    /** @return the token document with this hash, or null if none exists (already expired/never issued). */
    PasswordResetToken findByTokenHash(String tokenHash);
}