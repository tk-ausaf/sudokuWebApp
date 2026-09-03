package com.ausaf.sudoku.service;

import com.ausaf.sudoku.entity.MultiplayerGameEndReason;
import com.ausaf.sudoku.entity.MultiplayerGameOutcome;
import com.ausaf.sudoku.entity.MultiplayerGameStatus;
import com.ausaf.sudoku.entity.MultiplayerParticipant;
import com.ausaf.sudoku.entity.PlayerSlot;
import com.ausaf.sudoku.security.CallerIdentity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.concurrent.ConcurrentTaskScheduler;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * White-box unit tests for {@link MultiplayerGameEngine}'s turn/timeout rules, built directly
 * against {@link ActiveGame}/{@link ActiveGameRegistry} fixtures rather than through HTTP/STOMP,
 * so no Spring context or MongoDB is needed. Deliberately lives in this package (mirroring
 * {@code com.ausaf.sudoku.service}, not {@code com.ausaf.sudoku.multiplayer} like the other new
 * multiplayer tests) because it needs to construct those two package-private types directly.
 */
class MultiplayerGameEngineTest {

    // A well-known valid, fully solved 9x9 grid (row-major), used as this test's fixed solution.
    private static final String SOLUTION =
            "534678912672195348198342567859761423426853791713924856961537284287419635345286179";

    private static final String GAME_ID = "game-1";
    private static final CallerIdentity PLAYER1_IDENTITY = CallerIdentity.ofGuest("p1-anon");
    private static final CallerIdentity PLAYER2_IDENTITY = CallerIdentity.ofGuest("p2-anon");

    private ActiveGameRegistry registry;
    private MultiplayerGameEngine engine;
    private ScheduledExecutorService schedulerExecutor;

    @BeforeEach
    void setUp() {
        registry = new ActiveGameRegistry();
        schedulerExecutor = Executors.newSingleThreadScheduledExecutor();

        IdentityResolver identityResolver = mock(IdentityResolver.class);
        when(identityResolver.resolve(PLAYER1_IDENTITY)).thenReturn(new ResolvedIdentity(null, "p1-anon"));
        when(identityResolver.resolve(PLAYER2_IDENTITY)).thenReturn(new ResolvedIdentity(null, "p2-anon"));

        engine = new MultiplayerGameEngine();
        ReflectionTestUtils.setField(engine, "registry", registry);
        ReflectionTestUtils.setField(engine, "persistenceService", mock(MultiplayerGamePersistenceService.class));
        ReflectionTestUtils.setField(engine, "identityResolver", identityResolver);
        ReflectionTestUtils.setField(engine, "messagingTemplate", mock(SimpMessagingTemplate.class));
        ReflectionTestUtils.setField(engine, "timeoutScheduler", new ConcurrentTaskScheduler(schedulerExecutor));
    }

    @AfterEach
    void tearDown() {
        schedulerExecutor.shutdownNow();
    }

    /** A correct digit fills the cell and hands the turn to the opponent, without ending the game. */
    @Test
    void correctMoveAdvancesTurnToOpponent() {
        ActiveGame game = newActiveGame(30);

        engine.applyMove(GAME_ID, PLAYER1_IDENTITY, 0, 0, Character.getNumericValue(SOLUTION.charAt(0)));

        assertThat(game.currentGrid[0]).isEqualTo(SOLUTION.charAt(0));
        assertThat(game.currentTurn).isEqualTo(PlayerSlot.PLAYER2);
        assertThat(game.status).isEqualTo(MultiplayerGameStatus.IN_PROGRESS);
    }

    /** A digit that doesn't match the solution ends the game immediately - the opponent wins. */
    @Test
    void wrongDigitEndsGameWithOpponentWinning() {
        ActiveGame game = newActiveGame(30);
        int correctValue = Character.getNumericValue(SOLUTION.charAt(0));
        int wrongValue = correctValue == 9 ? 1 : correctValue + 1;

        engine.applyMove(GAME_ID, PLAYER1_IDENTITY, 0, 0, wrongValue);

        assertThat(game.status).isEqualTo(MultiplayerGameStatus.COMPLETED);
        assertThat(game.outcome).isEqualTo(MultiplayerGameOutcome.PLAYER2_WIN);
        assertThat(game.endReason).isEqualTo(MultiplayerGameEndReason.WRONG_MOVE);
        assertThat(registry.get(GAME_ID)).isNull();
    }

    /** A player who submits nothing before their deadline loses the game by timeout. */
    @Test
    void missedDeadlineEndsGameAsTimeoutLoss() throws InterruptedException {
        ActiveGame game = newActiveGame(1);
        engine.scheduleTimeout(game);

        awaitGameRemoved();

        assertThat(game.status).isEqualTo(MultiplayerGameStatus.COMPLETED);
        assertThat(game.outcome).isEqualTo(MultiplayerGameOutcome.PLAYER2_WIN);
        assertThat(game.endReason).isEqualTo(MultiplayerGameEndReason.TIMEOUT);
    }

    /** A move submitted after the wall-clock deadline has already passed is rejected the same as a timeout, not scored. */
    @Test
    void moveArrivingAfterDeadlineIsTreatedAsTimeoutLoss() {
        ActiveGame game = newActiveGame(30);
        game.turnDeadline = LocalDateTime.now().minusSeconds(1);

        engine.applyMove(GAME_ID, PLAYER1_IDENTITY, 0, 0, Character.getNumericValue(SOLUTION.charAt(0)));

        assertThat(game.status).isEqualTo(MultiplayerGameStatus.COMPLETED);
        assertThat(game.outcome).isEqualTo(MultiplayerGameOutcome.PLAYER2_WIN);
        assertThat(game.endReason).isEqualTo(MultiplayerGameEndReason.TIMEOUT);
    }

    /** Correctly filling the very last blank cell ends the game as a no-winner draw, not a win. */
    @Test
    void lastCorrectMoveEndsGameAsDrawWithNoWinner() {
        char[] almostFull = SOLUTION.toCharArray();
        almostFull[80] = '0';
        MultiplayerParticipant player1 = new MultiplayerParticipant(null, "p1-anon");
        ActiveGame game = new ActiveGame(GAME_ID, almostFull, SOLUTION.toCharArray(), player1, 30,
                MultiplayerGameStatus.IN_PROGRESS, LocalDateTime.now());
        game.player2 = new MultiplayerParticipant(null, "p2-anon");
        game.currentTurn = PlayerSlot.PLAYER1;
        game.turnDeadline = LocalDateTime.now().plusSeconds(30);
        registry.put(game);

        engine.applyMove(GAME_ID, PLAYER1_IDENTITY, 8, 8, Character.getNumericValue(SOLUTION.charAt(80)));

        assertThat(game.status).isEqualTo(MultiplayerGameStatus.COMPLETED);
        assertThat(game.outcome).isEqualTo(MultiplayerGameOutcome.DRAW);
        assertThat(game.endReason).isEqualTo(MultiplayerGameEndReason.BOARD_COMPLETE);
        assertThat(registry.get(GAME_ID)).isNull();
    }

    /** A move submitted by the player who does NOT currently hold the turn is rejected as noise, without ending the game. */
    @Test
    void outOfTurnMoveIsRejectedWithoutEndingGame() {
        ActiveGame game = newActiveGame(30);

        assertThrows(MultiplayerMoveRejectedException.class,
                () -> engine.applyMove(GAME_ID, PLAYER2_IDENTITY, 1, 1, 7));

        assertThat(game.status).isEqualTo(MultiplayerGameStatus.IN_PROGRESS);
        assertThat(registry.get(GAME_ID)).isNotNull();
    }

    /** Blocks until the timed-out game is removed from the registry (i.e. {@code endGame} ran), or fails after 5s. */
    private void awaitGameRemoved() throws InterruptedException {
        long deadlineMs = System.currentTimeMillis() + 5000;
        while (registry.get(GAME_ID) != null) {
            if (System.currentTimeMillis() > deadlineMs) {
                throw new AssertionError("Timed-out game was never removed from the registry");
            }
            Thread.sleep(50);
        }
    }

    private ActiveGame newActiveGame(int moveTimeLimitSeconds) {
        char[] clue = "0".repeat(81).toCharArray();
        MultiplayerParticipant player1 = new MultiplayerParticipant(null, "p1-anon");
        ActiveGame game = new ActiveGame(GAME_ID, clue, SOLUTION.toCharArray(), player1, moveTimeLimitSeconds,
                MultiplayerGameStatus.IN_PROGRESS, LocalDateTime.now());
        game.player2 = new MultiplayerParticipant(null, "p2-anon");
        game.currentTurn = PlayerSlot.PLAYER1;
        game.turnDeadline = LocalDateTime.now().plusSeconds(moveTimeLimitSeconds);
        game.startedAt = LocalDateTime.now();
        registry.put(game);
        return game;
    }
}