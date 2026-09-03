package com.ausaf.sudoku.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

/** Request body for {@code POST /multiplayer/games}: the creator's chosen per-move time limit. */
@Data
@NoArgsConstructor
public class MultiplayerCreateGameRequest {
    private int moveTimeLimitSeconds;
}
