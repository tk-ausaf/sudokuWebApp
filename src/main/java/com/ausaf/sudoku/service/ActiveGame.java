package com.ausaf.sudoku.service;

import com.ausaf.sudoku.entity.MultiplayerGameEndReason;
import com.ausaf.sudoku.entity.MultiplayerGameOutcome;
import com.ausaf.sudoku.entity.MultiplayerGameStatus;
import com.ausaf.sudoku.entity.MultiplayerParticipant;
import com.ausaf.sudoku.entity.PlayerSlot;

import java.time.LocalDateTime;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.locks.ReentrantLock;

/**
 * In-memory authoritative state for one active (waiting or in-progress) multiplayer game. While
 * a game is active, this - not its Mongo document - is the source of truth; every mutation must
 * hold {@link #lock} first, so a real move and a scheduled turn timeout can never interleave.
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

    MultiplayerGameStatus status;
    PlayerSlot currentTurn;
    LocalDateTime turnDeadline;
    int turnVersion;
    ScheduledFuture<?> pendingTimeout;

    MultiplayerGameOutcome outcome;
    MultiplayerGameEndReason endReason;

    final LocalDateTime createdAt;
    LocalDateTime startedAt;
    LocalDateTime endedAt;

    ActiveGame(String id, char[] clueGrid, char[] solutionGrid, MultiplayerParticipant player1,
               int moveTimeLimitSeconds, MultiplayerGameStatus status, LocalDateTime createdAt) {
        this.id = id;
        this.clueGrid = clueGrid;
        this.solutionGrid = solutionGrid;
        this.currentGrid = clueGrid.clone();
        this.player1 = player1;
        this.moveTimeLimitSeconds = moveTimeLimitSeconds;
        this.status = status;
        this.createdAt = createdAt;
    }
}