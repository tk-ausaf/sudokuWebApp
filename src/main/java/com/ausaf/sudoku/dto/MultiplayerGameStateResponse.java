package com.ausaf.sudoku.dto;

import com.ausaf.sudoku.entity.MultiplayerGameEndReason;
import com.ausaf.sudoku.entity.MultiplayerGameOutcome;
import com.ausaf.sudoku.entity.MultiplayerGameStatus;
import com.ausaf.sudoku.entity.PlayerSlot;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Full current state of one game, returned by {@code GET /multiplayer/games/{id}} and on join.
 * Deliberately excludes the solution grid - only {@code clueGrid} and {@code currentGrid} are
 * exposed to clients.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MultiplayerGameStateResponse {
    private String gameId;
    private String clueGrid;
    private String currentGrid;
    private MultiplayerGameStatus status;
    private PlayerSlot currentTurn;
    private LocalDateTime turnDeadline;
    private int moveTimeLimitSeconds;
    private MultiplayerGameOutcome outcome;
    private MultiplayerGameEndReason endReason;
    /** Which slot the requesting caller occupies, or null if they're neither participant. */
    private PlayerSlot yourSlot;
    private boolean player2Joined;
}