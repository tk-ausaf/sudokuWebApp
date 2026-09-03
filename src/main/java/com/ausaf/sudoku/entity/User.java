package com.ausaf.sudoku.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.web.bind.annotation.CrossOrigin;

/** A registered account: username plus a BCrypt-hashed password. */
@Data
@Document(collection = "users")
@CrossOrigin(origins = "*")
@NoArgsConstructor
public class User {

    @Id
    String id;
    String name;
    String password;

    /** Creates a user with a plaintext password - callers must BCrypt-hash it before saving. */
    public User(String name, String password) {
        this.name = name;
        this.password = password;
    }

}
