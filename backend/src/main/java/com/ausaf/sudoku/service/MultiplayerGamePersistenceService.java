package com.ausaf.sudoku.service;

import com.ausaf.sudoku.entity.MultiplayerGame;
import com.ausaf.sudoku.entity.MultiplayerGameEndReason;
import com.ausaf.sudoku.entity.MultiplayerGameOutcome;
import com.ausaf.sudoku.entity.MultiplayerGameStatus;
import com.ausaf.sudoku.entity.MultiplayerMove;
import com.ausaf.sudoku.entity.MultiplayerParticipant;
import com.ausaf.sudoku.entity.PlayerSlot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;

import static org.springframework.data.mongodb.core.query.Criteria.where;

/**
 * Writes an active game's state to its {@code multiplayer_games} document. Move results are
 * persisted off the calling thread (see {@link #persistMove}) so a player's move is broadcast and
 * acknowledged without waiting on the database; a targeted {@code $set}/{@code $push} update is
 * used instead of re-saving the whole entity, to avoid a read-modify-write race with a concurrent
 * write to the same document.
 */
@Slf4j
@Service
public class MultiplayerGamePersistenceService {

    @Autowired
    private MongoTemplate mongoTemplate;

    /**
     * Persists the result of one accepted move (correct, wrong-but-within-allowance, or a
     * game-ending move) and, when it ended the game, the final outcome. Runs on the dedicated
     * {@code multiplayerGameExecutor} pool, never the calling (STOMP handler) thread. {@code move}
     * is null for a timeout, which has no submitted move to record.
     */
    @Async("multiplayerGameExecutor")
    public void persistMove(String gameId, String currentGrid, MultiplayerGameStatus status,
                             PlayerSlot currentTurn, Instant turnDeadline,
                             MultiplayerGameOutcome outcome, MultiplayerGameEndReason endReason,
                             Instant endedAt, MultiplayerMove move,
                             int player1WrongAttempts, int player2WrongAttempts) {
        Update update = new Update()
                .set("currentGrid", currentGrid)
                .set("status", status)
                .set("currentTurn", currentTurn)
                .set("turnDeadline", turnDeadline)
                .set("outcome", outcome)
                .set("endReason", endReason)
                .set("endedAt", endedAt)
                .set("player1WrongAttempts", player1WrongAttempts)
                .set("player2WrongAttempts", player2WrongAttempts);
        if (move != null) {
            update.push("moveHistory", move);
        }
        mongoTemplate.updateFirst(Query.query(where("id").is(gameId)), update, MultiplayerGame.class);
        log.debug("Persisted move snapshot for game {} (status={})", gameId, status);
    }

    /** Persists a waiting game transitioning to IN_PROGRESS once the second player joins. */
    public void persistGameStarted(String gameId, MultiplayerParticipant player2, MultiplayerGameStatus status,
                                    PlayerSlot currentTurn, Instant turnDeadline, Instant startedAt) {
        Update update = new Update()
                .set("player2", player2)
                .set("status", status)
                .set("currentTurn", currentTurn)
                .set("turnDeadline", turnDeadline)
                .set("startedAt", startedAt);
        mongoTemplate.updateFirst(Query.query(where("id").is(gameId)), update, MultiplayerGame.class);
        log.debug("Persisted game-started snapshot for game {}", gameId);
    }
}