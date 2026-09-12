package com.ausaf.sudoku.service;

import com.ausaf.sudoku.entity.MultiplayerGameEndReason;
import com.ausaf.sudoku.entity.MultiplayerGameOutcome;
import com.ausaf.sudoku.entity.MultiplayerGameStatus;
import com.ausaf.sudoku.entity.MultiplayerParticipant;
import com.ausaf.sudoku.entity.PlayerSlot;

import java.time.Instant;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.locks.ReentrantLock;

/**
 * The one and only representation of an active (waiting or in-progress) multiplayer game -
 * nothing about it is persisted, so once it's removed from {@link ActiveGameRegistry} (a
 * completed game, or the process restarting) it simply ceases to exist. Every mutation must hold
 * {@link #lock} first, so a real move and a scheduled turn timeout can never interleave.
 * {@code turnVersion} increments on every turn change so a previously-scheduled timeout can
 * detect it has been superseded and become a no-op.
 */
class ActiveGame {

    final String id;
    final ReentrantLock lock = new ReentrantLock();

    final char[] clueGrid;
    final char[] solutionGrid;
    final char[] currentGrid;

    MultiplayerParticipant player1;
    MultiplayerParticipant player2;
    final int moveTimeLimitSeconds;
    final int maxWrongAttempts;

    MultiplayerGameStatus status;
    PlayerSlot currentTurn;
    Instant turnDeadline;
    int turnVersion;
    ScheduledFuture<?> pendingTimeout;

    /** Total moves resolved so far (correct or wrong-but-within-allowance) - see {@link MultiplayerGameEngine}. */
    int movesMade;
    int player1WrongAttempts;
    int player2WrongAttempts;

    MultiplayerGameOutcome outcome;
    MultiplayerGameEndReason endReason;

    ActiveGame(String id, char[] clueGrid, char[] solutionGrid, MultiplayerParticipant player1,
               int moveTimeLimitSeconds, int maxWrongAttempts, MultiplayerGameStatus status) {
        this.id = id;
        this.clueGrid = clueGrid;
        this.solutionGrid = solutionGrid;
        this.currentGrid = clueGrid.clone();
        this.player1 = player1;
        this.moveTimeLimitSeconds = moveTimeLimitSeconds;
        this.maxWrongAttempts = maxWrongAttempts;
        this.status = status;
    }
}
