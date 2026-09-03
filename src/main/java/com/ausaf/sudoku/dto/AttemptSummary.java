package com.ausaf.sudoku.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** One row of {@code GET /sudoku/attempts} (resume/history list) - status only, no grid payload. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AttemptSummary {
    private String attemptId;
    private boolean completed;
    private LocalDateTime assignedAt;
    private LocalDateTime completedAt;
    private boolean hasProgress;
}