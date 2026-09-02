package com.ausaf.sudoku.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResumeResponse {
    private String attemptId;
    /** 81 chars, original clue grid, '0' = blank. */
    private String clueGrid;
    /** 81 chars, latest autosaved grid (falls back to clueGrid if never autosaved). */
    private String currentGrid;
    private boolean completed;
}