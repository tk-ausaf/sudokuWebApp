package com.ausaf.sudoku.dto;

import com.ausaf.sudoku.entity.MultiplayerGameEndReason;
import com.ausaf.sudoku.entity.MultiplayerGameOutcome;
import com.ausaf.sudoku.entity.PlayerSlot;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * The broadcast envelope published to {@code /topic/games/{gameId}} for every player-visible
 * change: a joined opponent, an accepted move, a wrong-but-within-allowance guess, or the game
 * ending. Kept small and delta-shaped (a single cell, not the whole grid) so every subscriber can
 * cheaply stay in sync.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MultiplayerGameEvent {
    /** One of {@code PLAYER_JOINED}, {@code MOVE_ACCEPTED}, {@code WRONG_MOVE}, {@code GAME_ENDED}. */
    private String eventType;
    private PlayerSlot actor;
    private Integer row;
    private Integer col;
    private Integer value;
    private PlayerSlot nextTurn;
    private LocalDateTime nextTurnDeadline;
    private MultiplayerGameOutcome outcome;
    private MultiplayerGameEndReason endReason;
    private Integer player1WrongAttempts;
    private Integer player2WrongAttempts;
}