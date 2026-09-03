package com.ausaf.sudoku.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One ranked row of {@code GET /sudoku/leaderboard}: a user's rank and puzzles solved in the period. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LeaderboardEntry {
    private int rank;
    private String displayName;
    private long solvedCount;
}