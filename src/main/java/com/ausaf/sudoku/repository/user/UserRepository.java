package com.ausaf.sudoku.repository.user;

import com.ausaf.sudoku.entity.User;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/** Spring Data MongoDB repository for {@link User} accounts. */
@Repository
public interface UserRepository extends MongoRepository<User, String> {
    /** @return the account with this username, or null if none exists. */
    User findByName(String name);

    /** @return the account linked to this Google subject ("sub") id, or null if none exists. */
    User findByGoogleId(String googleId);

    /** @return the account with this recovery email on file, or null if none exists. */
    User findByEmail(String email);
}
