package com.ausaf.sudoku.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Response for {@code GET /sudoku/puzzle}: the caller's active or newly generated puzzle attempt. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PuzzleResponse {
    private String attemptId;
    /** 81 chars, row-major, '0' = blank cell, digit = given clue (read-only for the client). */
    private String clueGrid;
    /** 81 chars, latest saved progress (falls back to clueGrid if nothing autosaved yet). */
    private String currentGrid;
}