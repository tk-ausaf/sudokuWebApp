package com.ausaf.sudoku.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for {@code POST /multiplayer/games}: the creator's chosen per-move time limit and
 * how many wrong guesses (across the whole game) a player may make before losing for it.
 */
@Data
@NoArgsConstructor
public class MultiplayerCreateGameRequest {
    private int moveTimeLimitSeconds;
    private int maxWrongAttempts;
}