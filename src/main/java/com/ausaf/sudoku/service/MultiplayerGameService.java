package com.ausaf.sudoku.service;

import com.ausaf.sudoku.dto.MultiplayerGameCreatedResponse;
import com.ausaf.sudoku.dto.MultiplayerGameEvent;
import com.ausaf.sudoku.dto.MultiplayerGameStateResponse;
import com.ausaf.sudoku.entity.MultiplayerGame;
import com.ausaf.sudoku.entity.MultiplayerGameStatus;
import com.ausaf.sudoku.entity.MultiplayerParticipant;
import com.ausaf.sudoku.entity.PlayerSlot;
import com.ausaf.sudoku.repository.multiplayer.MultiplayerGameRepository;
import com.ausaf.sudoku.security.CallerIdentity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * REST-facing lifecycle operations for a multiplayer game: creating one (generating a
 * unique-solution puzzle), a second player joining via its shareable link, and reading its
 * current state. Once a game is {@code IN_PROGRESS}, move handling belongs to
 * {@link MultiplayerGameEngine} instead. Entirely separate from {@link SudokuService} and
 * {@link LeaderboardService} - multiplayer games live in their own {@code multiplayer_games}
 * collection and never touch {@code puzzle_attempts}.
 */
@Service
public class MultiplayerGameService {

    private static final int CELLS_TO_REMOVE = 45;
    private static final int MIN_TIME_LIMIT_SECONDS = 5;
    private static final int MAX_TIME_LIMIT_SECONDS = 600;

    @Autowired
    private UniqueSolutionSudokuGenerator puzzleGenerator;

    @Autowired
    private MultiplayerGameRepository gameRepository;

    @Autowired
    private ActiveGameRegistry registry;

    @Autowired
    private MultiplayerGameEngine engine;

    @Autowired
    private MultiplayerGamePersistenceService persistenceService;

    @Autowired
    private IdentityResolver identityResolver;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    /**
     * Generates a fresh unique-solution puzzle and creates a new game with the caller as player 1.
     *
     * @throws ResponseStatusException 400 if the requested time limit is outside the allowed bounds
     */
    public MultiplayerGameCreatedResponse createGame(CallerIdentity identity, int moveTimeLimitSeconds) {
        if (moveTimeLimitSeconds < MIN_TIME_LIMIT_SECONDS || moveTimeLimitSeconds > MAX_TIME_LIMIT_SECONDS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "moveTimeLimitSeconds must be between " + MIN_TIME_LIMIT_SECONDS + " and " + MAX_TIME_LIMIT_SECONDS);
        }
        ResolvedIdentity owner = identityResolver.resolve(identity);
        GeneratedMultiplayerPuzzle puzzle = puzzleGenerator.generate(CELLS_TO_REMOVE);

        MultiplayerGame gameDoc = new MultiplayerGame();
        gameDoc.setPlayer1(toParticipant(owner));
        gameDoc.setClueGrid(puzzle.clueGrid());
        gameDoc.setSolutionGrid(puzzle.solutionGrid());
        gameDoc.setCurrentGrid(puzzle.clueGrid());
        gameDoc.setMoveTimeLimitSeconds(moveTimeLimitSeconds);
        gameDoc.setStatus(MultiplayerGameStatus.WAITING_FOR_OPPONENT);
        gameDoc.setCreatedAt(LocalDateTime.now());
        gameRepository.save(gameDoc);

        ActiveGame active = new ActiveGame(gameDoc.getId(), puzzle.clueGrid().toCharArray(),
                puzzle.solutionGrid().toCharArray(), gameDoc.getPlayer1(), moveTimeLimitSeconds,
                MultiplayerGameStatus.WAITING_FOR_OPPONENT, gameDoc.getCreatedAt());
        registry.put(active);

        return new MultiplayerGameCreatedResponse(gameDoc.getId(), puzzle.clueGrid(),
                moveTimeLimitSeconds, MultiplayerGameStatus.WAITING_FOR_OPPONENT);
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
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Game not found");
        }
        ResolvedIdentity joiner = identityResolver.resolve(identity);
        MultiplayerParticipant participant = toParticipant(joiner);

        game.lock.lock();
        try {
            if (game.status != MultiplayerGameStatus.WAITING_FOR_OPPONENT) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Game already has two players");
            }
            if (sameIdentity(game.player1, participant)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You already created this game");
            }

            game.player2 = participant;
            game.status = MultiplayerGameStatus.IN_PROGRESS;
            game.currentTurn = PlayerSlot.PLAYER1;
            game.startedAt = LocalDateTime.now();
            game.turnDeadline = game.startedAt.plusSeconds(game.moveTimeLimitSeconds);
            engine.scheduleTimeout(game);

            persistenceService.persistGameStarted(gameId, participant, game.status, game.currentTurn,
                    game.turnDeadline, game.startedAt);

            messagingTemplate.convertAndSend("/topic/games/" + gameId, new MultiplayerGameEvent(
                    "PLAYER_JOINED", PlayerSlot.PLAYER2, null, null, null, game.currentTurn, game.turnDeadline, null, null));

            return toStateResponse(game, PlayerSlot.PLAYER2);
        } finally {
            game.lock.unlock();
        }
    }

    /**
     * Current state of a game, read from the in-memory registry when active (this is always the
     * freshest view - Mongo may briefly lag due to async move persistence), else falling back to
     * the persisted document for a completed game.
     */
    public MultiplayerGameStateResponse getState(CallerIdentity identity, String gameId) {
        ResolvedIdentity caller = identityResolver.resolve(identity);
        MultiplayerParticipant callerParticipant = toParticipant(caller);

        ActiveGame game = registry.get(gameId);
        if (game != null) {
            game.lock.lock();
            try {
                PlayerSlot yourSlot = slotOf(game, callerParticipant);
                return toStateResponse(game, yourSlot);
            } finally {
                game.lock.unlock();
            }
        }

        MultiplayerGame doc = gameRepository.findById(gameId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Game not found"));
        PlayerSlot yourSlot = sameIdentity(doc.getPlayer1(), callerParticipant) ? PlayerSlot.PLAYER1
                : sameIdentity(doc.getPlayer2(), callerParticipant) ? PlayerSlot.PLAYER2 : null;
        return new MultiplayerGameStateResponse(doc.getId(), doc.getClueGrid(), doc.getCurrentGrid(),
                doc.getStatus(), doc.getCurrentTurn(), doc.getTurnDeadline(), doc.getMoveTimeLimitSeconds(),
                doc.getOutcome(), doc.getEndReason(), yourSlot, doc.getPlayer2() != null);
    }

    private MultiplayerGameStateResponse toStateResponse(ActiveGame game, PlayerSlot yourSlot) {
        return new MultiplayerGameStateResponse(game.id, new String(game.clueGrid), new String(game.currentGrid),
                game.status, game.currentTurn, game.turnDeadline, game.moveTimeLimitSeconds,
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