package com.ausaf.sudoku.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Document(collection = "notifications")
@NoArgsConstructor
public class Notification {

    @Id
    private String id;
    private String title;
    private LocalDateTime date;
    private String content;
    private String imageUrl;

    public Notification(String title, String content) {
        this.title = title;
        this.content = content;
        this.date = LocalDateTime.now();
    }
}
