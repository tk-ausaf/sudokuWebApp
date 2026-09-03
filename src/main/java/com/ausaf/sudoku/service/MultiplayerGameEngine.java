package com.ausaf.sudoku.service;

import com.ausaf.sudoku.dto.MultiplayerGameEvent;
import com.ausaf.sudoku.entity.MultiplayerGameEndReason;
import com.ausaf.sudoku.entity.MultiplayerGameOutcome;
import com.ausaf.sudoku.entity.MultiplayerGameStatus;
import com.ausaf.sudoku.entity.MultiplayerMove;
import com.ausaf.sudoku.entity.MultiplayerParticipant;
import com.ausaf.sudoku.entity.PlayerSlot;
import com.ausaf.sudoku.security.CallerIdentity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Objects;
import java.util.concurrent.ScheduledFuture;

/**
 * Owns the live rules of an active multiplayer game: validating a submitted move against
 * participant identity, turn ownership, the deadline, and the puzzle's unique solution; advancing
 * or ending the turn; broadcasting the result; and enforcing the per-turn timeout. All mutation of
 * a given game's {@link ActiveGame} happens under that game's own lock, so a real move and its
 * scheduled timeout can never interleave - see {@link #handleTimeout} for how that race resolves.
 */
@Service
public class MultiplayerGameEngine {

    @Autowired
    private ActiveGameRegistry registry;

    @Autowired
    private MultiplayerGamePersistenceService persistenceService;

    @Autowired
    private IdentityResolver identityResolver;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    @Qualifier("multiplayerTimeoutScheduler")
    private TaskScheduler timeoutScheduler;

    /**
     * Validates and applies one player's submitted cell value.
     *
     * @throws MultiplayerMoveRejectedException if the caller isn't a participant, it isn't their
     *         turn, the game isn't in progress, or the coordinates/target cell are invalid - this
     *         is treated as client noise, not a real move, and never ends the game. A late arrival
     *         past the deadline, or a wrong digit, is a real move attempt and always ends the game.
     */
    public void applyMove(String gameId, CallerIdentity identity, int row, int col, int value) {
        ActiveGame game = registry.get(gameId);
        if (game == null) {
            throw new MultiplayerMoveRejectedException("Game not found or not active");
        }

        ResolvedIdentity resolved = identityResolver.resolve(identity);
        MultiplayerParticipant participant = new MultiplayerParticipant(resolved.getUserId(), resolved.getAnonymousId());

        game.lock.lock();
        try {
            PlayerSlot caller = slotOf(game, participant);
            if (caller == null) {
                throw new MultiplayerMoveRejectedException("You are not a participant in this game");
            }
            if (game.status != MultiplayerGameStatus.IN_PROGRESS) {
                throw new MultiplayerMoveRejectedException("Game is not in progress");
            }
            if (caller != game.currentTurn) {
                throw new MultiplayerMoveRejectedException("Not your turn");
            }
            if (row < 0 || row > 8 || col < 0 || col > 8 || value < 1 || value > 9) {
                throw new MultiplayerMoveRejectedException("Move out of range");
            }
            int cellIndex = row * 9 + col;
            if (game.currentGrid[cellIndex] != '0') {
                throw new MultiplayerMoveRejectedException("Cell already filled");
            }

            LocalDateTime now = LocalDateTime.now();
            if (game.turnDeadline != null && now.isAfter(game.turnDeadline)) {
                endGameForTimeout(game, caller);
                return;
            }

            boolean correct = game.solutionGrid[cellIndex] == Character.forDigit(value, 10);
            MultiplayerMove move = new MultiplayerMove(caller, row, col, value, correct, now);

            if (!correct) {
                endGame(game, opponentOf(caller), MultiplayerGameEndReason.WRONG_MOVE, move);
                return;
            }

            game.currentGrid[cellIndex] = Character.forDigit(value, 10);
            cancelPendingTimeout(game);

            if (isBoardFull(game.currentGrid)) {
                endGame(game, MultiplayerGameOutcome.DRAW, MultiplayerGameEndReason.BOARD_COMPLETE, move);
                return;
            }

            game.currentTurn = opponentOf(caller);
            game.turnVersion++;
            game.turnDeadline = now.plusSeconds(game.moveTimeLimitSeconds);
            scheduleTimeout(game);

            MultiplayerGameEvent event = new MultiplayerGameEvent(
                    "MOVE_ACCEPTED", caller, row, col, value, game.currentTurn, game.turnDeadline, null, null);
            broadcast(game.id, event);
            persistenceService.persistMove(game.id, new String(game.currentGrid), game.status,
                    game.currentTurn, game.turnDeadline, game.outcome, game.endReason, game.endedAt, move);
        } finally {
            game.lock.unlock();
        }
    }

    /**
     * Schedules the timeout task for the game's current turn, capturing its {@code turnVersion}
     * so a stale firing (superseded by a real move) becomes a no-op. Must be called while
     * holding {@code game.lock}; used both when a turn starts after a join and after every
     * accepted correct move.
     */
    void scheduleTimeout(ActiveGame game) {
        int expectedVersion = game.turnVersion;
        Instant deadline = game.turnDeadline.atZone(ZoneId.systemDefault()).toInstant();
        game.pendingTimeout = timeoutScheduler.schedule(() -> handleTimeout(game.id, expectedVersion), deadline);
    }

    /**
     * Fires when a scheduled turn deadline is reached. Re-checks {@code turnVersion} under the
     * game's lock before acting: if a real move already advanced the turn (or ended the game)
     * since this task was scheduled, {@code turnVersion} will have changed and this is a no-op -
     * so whichever of a late move or this timeout reaches the lock first, the outcome is the same.
     */
    private void handleTimeout(String gameId, int expectedTurnVersion) {
        ActiveGame game = registry.get(gameId);
        if (game == null) {
            return;
        }
        game.lock.lock();
        try {
            if (game.status != MultiplayerGameStatus.IN_PROGRESS || game.turnVersion != expectedTurnVersion) {
                return;
            }
            endGameForTimeout(game, game.currentTurn);
        } finally {
            game.lock.unlock();
        }
    }

    /** Ends the game as a timeout loss for {@code loser}; must be called while holding {@code game.lock}. */
    private void endGameForTimeout(ActiveGame game, PlayerSlot loser) {
        endGame(game, opponentOf(loser), MultiplayerGameEndReason.TIMEOUT, null);
    }

    /** Ends the game with {@code winner} taking it; must be called while holding {@code game.lock}. */
    private void endGame(ActiveGame game, PlayerSlot winner, MultiplayerGameEndReason reason, MultiplayerMove move) {
        MultiplayerGameOutcome outcome = winner == PlayerSlot.PLAYER1
                ? MultiplayerGameOutcome.PLAYER1_WIN
                : MultiplayerGameOutcome.PLAYER2_WIN;
        endGame(game, outcome, reason, move);
    }

    /** Ends the game with an explicit outcome (win or draw); must be called while holding {@code game.lock}. */
    private void endGame(ActiveGame game, MultiplayerGameOutcome outcome, MultiplayerGameEndReason reason, MultiplayerMove move) {
        cancelPendingTimeout(game);
        game.status = MultiplayerGameStatus.COMPLETED;
        game.outcome = outcome;
        game.endReason = reason;
        game.endedAt = LocalDateTime.now();
        game.turnDeadline = null;

        MultiplayerGameEvent event = new MultiplayerGameEvent(
                "GAME_ENDED", null, null, null, null, null, null, outcome, reason);
        broadcast(game.id, event);
        persistenceService.persistMove(game.id, new String(game.currentGrid), game.status,
                game.currentTurn, game.turnDeadline, game.outcome, game.endReason, game.endedAt, move);
        registry.remove(game.id);
    }

    /** Cancels any pending scheduled timeout for this game; safe to call even if none is pending. */
    private void cancelPendingTimeout(ActiveGame game) {
        ScheduledFuture<?> pending = game.pendingTimeout;
        if (pending != null) {
            pending.cancel(false);
            game.pendingTimeout = null;
        }
    }

    private boolean isBoardFull(char[] grid) {
        for (char c : grid) {
            if (c == '0') {
                return false;
            }
        }
        return true;
    }

    private PlayerSlot opponentOf(PlayerSlot slot) {
        return slot == PlayerSlot.PLAYER1 ? PlayerSlot.PLAYER2 : PlayerSlot.PLAYER1;
    }

    /** @return which slot {@code participant} occupies in {@code game}, or null if they're neither player. */
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

    private void broadcast(String gameId, MultiplayerGameEvent event) {
        messagingTemplate.convertAndSend("/topic/games/" + gameId, event);
    }
}
