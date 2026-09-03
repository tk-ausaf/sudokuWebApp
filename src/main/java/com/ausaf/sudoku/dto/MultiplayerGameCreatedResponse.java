package com.ausaf.sudoku.dto;

import com.ausaf.sudoku.entity.MultiplayerGameStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Response for {@code POST /multiplayer/games}: the new game's id (also its shareable-link token) and clue grid. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MultiplayerGameCreatedResponse {
    private String gameId;
    private String clueGrid;
    private int moveTimeLimitSeconds;
    private MultiplayerGameStatus status;
}