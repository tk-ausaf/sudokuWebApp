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
    private boolean failed;
    private boolean abandoned;
    /** Player-chosen label, if this attempt has been explicitly saved with one; null otherwise. */
    private String name;
}