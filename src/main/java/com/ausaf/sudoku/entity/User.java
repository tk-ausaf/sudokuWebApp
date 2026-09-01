package com.ausaf.sudoku.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.web.bind.annotation.CrossOrigin;

@Data
@Document(collection = "users")
@CrossOrigin(origins = "*")
@NoArgsConstructor
public class User {

    @Id
    String id;
    String name;
    String password;

    public User(String name, String password) {
        this.name = name;
        this.password = password;
    }

}
