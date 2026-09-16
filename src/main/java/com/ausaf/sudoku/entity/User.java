package com.ausaf.sudoku.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.web.bind.annotation.CrossOrigin;

/**
 * A registered account, identified by a unique {@code name}. Authenticated either by a
 * BCrypt-hashed {@code password} (username/password accounts) or by a linked Google identity
 * (Google accounts) - exactly one of {@code password}/{@code googleId} is expected to be set.
 */
@Data
@Document(collection = "users")
@CrossOrigin(origins = "*")
@NoArgsConstructor
public class User {

    @Id
    String id;
    String name;
    String password;

    /** Google's stable per-account subject ("sub") id - null for username/password accounts. */
    String googleId;

    /** Creates a user with a plaintext password - callers must BCrypt-hash it before saving. */
    public User(String name, String password) {
        this.name = name;
        this.password = password;
    }

}
