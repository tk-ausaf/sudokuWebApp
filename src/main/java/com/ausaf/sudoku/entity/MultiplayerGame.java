package com.ausaf.sudoku.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A single 2-player turn-based Sudoku match, stored separately from {@link PuzzleAttempt} in its
 * own {@code multiplayer_games} collection so it never interacts with single-player gameplay or
 * the leaderboard. The document id doubles as the shareable join link's token. {@code solutionGrid}
 * is the puzzle's one and only valid solution (guaranteed unique at generation time) and must never
 * be exposed to clients. While a game is {@code IN_PROGRESS}, the authoritative copy of this state
 * lives in-memory ({@code ActiveGameRegistry}) - this document is a write-behind snapshot, so a
 * caller reading it directly (rather than through the game engine) may briefly lag the live state.
 */
@Data
@Document(collection = "multiplayer_games")
@NoArgsConstructor
public class MultiplayerGame {

    @Id
    private String id;

    private MultiplayerParticipant player1;
    private MultiplayerParticipant player2;

    /** 81 chars, row-major, '0' = blank cell, digit = given clue. */
    private String clueGrid;
    /** 81 chars, row-major, the puzzle's single valid solution - never sent to clients. */
    private String solutionGrid;
    /** 81 chars, row-major, cells filled so far by either player; '0' = still blank. */
    private String currentGrid;

    private int moveTimeLimitSeconds;

    private MultiplayerGameStatus status;
    private PlayerSlot currentTurn;
    private LocalDateTime turnDeadline;

    private MultiplayerGameOutcome outcome;
    private MultiplayerGameEndReason endReason;

    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;

    private List<MultiplayerMove> moveHistory = new ArrayList<>();
}