package com.ausaf.sudoku.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The delta payload a client sends over STOMP to {@code /app/games/{gameId}/move}: one cell
 * position and the digit placed there - never the whole grid.
 */
@Data
@NoArgsConstructor
public class MultiplayerMoveRequest {
    private int row;
    private int col;
    private int value;
}