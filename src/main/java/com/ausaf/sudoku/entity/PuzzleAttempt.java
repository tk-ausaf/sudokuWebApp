package com.ausaf.sudoku.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/** Tracks which puzzle a user was assigned, so repeats can be avoided. */
@Data
@Document(collection = "puzzle_attempts")
@NoArgsConstructor
public class PuzzleAttempt {

    @Id
    private String id;

    private String userId;
    private String puzzleId;
    private boolean completed;
    private LocalDateTime assignedAt;
    private LocalDateTime completedAt;
}