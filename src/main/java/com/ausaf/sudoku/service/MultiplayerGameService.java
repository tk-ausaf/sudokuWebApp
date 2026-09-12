package com.ausaf.sudoku.service;

import com.ausaf.sudoku.dto.MultiplayerGameCreatedResponse;
import com.ausaf.sudoku.dto.MultiplayerGameEvent;
import com.ausaf.sudoku.dto.MultiplayerGameStateResponse;
import com.ausaf.sudoku.entity.MultiplayerGameStatus;
import com.ausaf.sudoku.entity.MultiplayerParticipant;
import com.ausaf.sudoku.entity.PlayerSlot;
import com.ausaf.sudoku.security.CallerIdentity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Objects;
import java.util.UUID;

/**
 * REST-facing lifecycle operations for a multiplayer game: creating one (generating a
 * unique-solution puzzle), a second player joining via its shareable link, and reading its
 * current state. Once a game is {@code IN_PROGRESS}, move handling belongs to
 * {@link MultiplayerGameEngine} instead. Entirely separate from {@link SudokuService} and
 * {@link LeaderboardService} - a multiplayer game exists only in memory (see
 * {@link ActiveGameRegistry}) for as long as it's waiting or in progress, is never persisted, and
 * is not resumable once it ends; only single-player attempts can be resumed.
 */
@Slf4j
@Service
public class MultiplayerGameService {

    private static final int CELLS_TO_REMOVE = 45;
    private static final int MIN_TIME_LIMIT_SECONDS = 5;
    private static final int MAX_TIME_LIMIT_SECONDS = 600;
    private static final int MIN_WRONG_ATTEMPTS = 1;
    private static final int MAX_WRONG_ATTEMPTS = 20;

    @Autowired
    private UniqueSolutionSudokuGenerator puzzleGenerator;

    @Autowired
    private ActiveGameRegistry registry;

    @Autowired
    private PuzzleBankService puzzleBankService;

    @Autowired
    private IdentityResolver identityResolver;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    /**
     * Generates a fresh unique-solution puzzle and creates a new game with the caller as player 1.
     *
     * @throws ResponseStatusException 400 if the requested time limit or wrong-attempt allowance
     *         is outside the allowed bounds
     */
    public MultiplayerGameCreatedResponse createGame(CallerIdentity identity, int moveTimeLimitSeconds,
                                                       int maxWrongAttempts) {
        if (moveTimeLimitSeconds < MIN_TIME_LIMIT_SECONDS || moveTimeLimitSeconds > MAX_TIME_LIMIT_SECONDS) {
            log.warn("Rejected game creation: moveTimeLimitSeconds={} out of range", moveTimeLimitSeconds);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "moveTimeLimitSeconds must be between " + MIN_TIME_LIMIT_SECONDS + " and " + MAX_TIME_LIMIT_SECONDS);
        }
        if (maxWrongAttempts < MIN_WRONG_ATTEMPTS || maxWrongAttempts > MAX_WRONG_ATTEMPTS) {
            log.warn("Rejected game creation: maxWrongAttempts={} out of range", maxWrongAttempts);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "maxWrongAttempts must be between " + MIN_WRONG_ATTEMPTS + " and " + MAX_WRONG_ATTEMPTS);
        }
        ResolvedIdentity owner = identityResolver.resolve(identity);
        GeneratedMultiplayerPuzzle puzzle = puzzleBankService.getRandomPuzzle().orElseGet(() -> {
            log.warn("Puzzle bank empty - falling back to live generation for game created by {}", owner.toLogString());
            return puzzleGenerator.generate(CELLS_TO_REMOVE);
        });

        String gameId = UUID.randomUUID().toString();
        ActiveGame active = new ActiveGame(gameId, puzzle.clueGrid().toCharArray(), puzzle.solutionGrid().toCharArray(),
                toParticipant(owner), moveTimeLimitSeconds, maxWrongAttempts, MultiplayerGameStatus.WAITING_FOR_OPPONENT);
        registry.put(active);

        log.info("Game {} created by {} (moveTimeLimitSeconds={}, maxWrongAttempts={})",
                gameId, owner.toLogString(), moveTimeLimitSeconds, maxWrongAttempts);
        return new MultiplayerGameCreatedResponse(gameId, puzzle.clueGrid(),
                moveTimeLimitSeconds, maxWrongAttempts, MultiplayerGameStatus.WAITING_FOR_OPPONENT);
    }

    /**
     * The caller joins an existing waiting game as player 2, which immediately starts play
     * (player 1 moves first).
     *
     * @throws ResponseStatusException 404 if the game doesn't exist or is no longer waiting for
     *         an opponent in this process, 409 if it already has two players, 400 if the caller
     *         is the same identity as player 1
     */
    public MultiplayerGameStateResponse joinGame(CallerIdentity identity, String gameId) {
        ActiveGame game = registry.get(gameId);
        if (game == null) {
            log.warn("Join attempt for unknown/inactive game {}", gameId);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Game not found");
        }
        ResolvedIdentity joiner = identityResolver.resolve(identity);
        MultiplayerParticipant participant = toParticipant(joiner);

        game.lock.lock();
        try {
            if (game.status != MultiplayerGameStatus.WAITING_FOR_OPPONENT) {
                log.warn("Join rejected for game {}: already has two players", gameId);
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Game already has two players");
            }
            if (sameIdentity(game.player1, participant)) {
                log.warn("Join rejected for game {}: creator tried to join their own game", gameId);
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You already created this game");
            }

            game.player2 = participant;
            game.status = MultiplayerGameStatus.IN_PROGRESS;
            game.currentTurn = PlayerSlot.PLAYER1;
            // Player 1's first move carries no deadline - see MultiplayerGameEngine.FIRST_MOVES_WITHOUT_DEADLINE.
            game.turnDeadline = null;

            messagingTemplate.convertAndSend("/topic/games/" + gameId, new MultiplayerGameEvent(
                    "PLAYER_JOINED", PlayerSlot.PLAYER2, null, null, null, game.currentTurn, game.turnDeadline,
                    null, null, game.player1WrongAttempts, game.player2WrongAttempts));

            log.info("Game {} started: {} joined as PLAYER2", gameId, joiner.toLogString());
            return toStateResponse(game, PlayerSlot.PLAYER2);
        } finally {
            game.lock.unlock();
        }
    }

    /**
     * Current state of a game, read from the in-memory registry - the only place it exists. A
     * game that's finished or was never created is simply gone, not resumable: this always 404s
     * for it rather than falling back to any persisted record.
     *
     * @throws ResponseStatusException 404 if the game isn't currently active
     */
    public MultiplayerGameStateResponse getState(CallerIdentity identity, String gameId) {
        ResolvedIdentity caller = identityResolver.resolve(identity);
        MultiplayerParticipant callerParticipant = toParticipant(caller);

        ActiveGame game = registry.get(gameId);
        if (game == null) {
            log.warn("State request for unknown/inactive game {}", gameId);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Game not found");
        }

        game.lock.lock();
        try {
            PlayerSlot yourSlot = slotOf(game, callerParticipant);
            return toStateResponse(game, yourSlot);
        } finally {
            game.lock.unlock();
        }
    }

    private MultiplayerGameStateResponse toStateResponse(ActiveGame game, PlayerSlot yourSlot) {
        return new MultiplayerGameStateResponse(game.id, new String(game.clueGrid), new String(game.currentGrid),
                game.status, game.currentTurn, game.turnDeadline, game.moveTimeLimitSeconds,
                game.maxWrongAttempts, game.player1WrongAttempts, game.player2WrongAttempts,
                game.outcome, game.endReason, yourSlot, game.player2 != null);
    }

    private PlayerSlot slotOf(ActiveGame game, MultiplayerParticipant participant) {
        if (sameIdentity(game.player1, participant)) {
            return PlayerSlot.PLAYER1;
        }
        if (sameIdentity(game.player2, participant)) {
            return PlayerSlot.PLAYER2;
        }
        return null;
    }

    private boolean sameIdentity(MultiplayerParticipant a, MultiplayerParticipant b) {
        if (a == null || b == null) {
            return false;
        }
        if (a.getUserId() != null) {
            return Objects.equals(a.getUserId(), b.getUserId());
        }
        return Objects.equals(a.getAnonymousId(), b.getAnonymousId());
    }

    private MultiplayerParticipant toParticipant(ResolvedIdentity identity) {
        return new MultiplayerParticipant(identity.getUserId(), identity.getAnonymousId());
    }
}
