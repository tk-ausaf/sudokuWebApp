package com.ausaf.sudoku.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * A single puzzle instance generated on demand for a user (or guest) - puzzles are never
 * shared or reused across users, so the clue grid is embedded directly rather than
 * referencing a separate puzzle document. Exactly one of {@code userId}/{@code anonymousId}
 * is the current owner; both may be set once a guest-originated attempt has been claimed by
 * an account (anonymousId is kept as an audit trail, not cleared). {@code assignedAt} is the
 * immutable true first-view time - it must never be updated once set, even when a guest
 * attempt is re-owned on login, since the leaderboard is measured from it.
 */
@Data
@Document(collection = "puzzle_attempts")
@NoArgsConstructor
public class PuzzleAttempt {

    @Id
    private String id;

    private String userId;
    private String anonymousId;

    /** 81 chars, row-major, '0' = blank cell, digit = given clue. */
    private String clueGrid;

    private boolean completed;
    private LocalDateTime assignedAt;
    private LocalDateTime completedAt;
    private LocalDateTime lastSavedAt;

    /** 81-char live in-progress grid, '0' = blank; autosaved on every cell change. */
    private String currentGrid;
}
