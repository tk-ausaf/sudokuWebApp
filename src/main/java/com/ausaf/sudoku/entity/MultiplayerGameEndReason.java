package com.ausaf.sudoku.entity;

/** Why a {@link MultiplayerGame} ended: a wrong digit, a missed turn deadline, or a fully solved board. */
public enum MultiplayerGameEndReason {
    WRONG_MOVE,
    TIMEOUT,
    BOARD_COMPLETE
}
