package com.ausaf.sudoku.entity;

/** Lifecycle state of a {@link MultiplayerGame}, from creation through to a finished game. */
public enum MultiplayerGameStatus {
    WAITING_FOR_OPPONENT,
    IN_PROGRESS,
    COMPLETED
}
