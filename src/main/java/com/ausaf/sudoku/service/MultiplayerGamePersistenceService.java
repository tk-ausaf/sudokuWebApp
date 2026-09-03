package com.ausaf.sudoku.service;

import com.ausaf.sudoku.entity.MultiplayerGame;
import com.ausaf.sudoku.entity.MultiplayerGameEndReason;
import com.ausaf.sudoku.entity.MultiplayerGameOutcome;
import com.ausaf.sudoku.entity.MultiplayerGameStatus;
import com.ausaf.sudoku.entity.MultiplayerMove;
import com.ausaf.sudoku.entity.MultiplayerParticipant;
import com.ausaf.sudoku.entity.PlayerSlot;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

import static org.springframework.data.mongodb.core.query.Criteria.where;

/**
 * Writes an active game's state to its {@code multiplayer_games} document. Move results are
 * persisted off the calling thread (see {@link #persistMove}) so a player's move is broadcast and
 * acknowledged without waiting on the database; a targeted {@code $set}/{@code $push} update is
 * used instead of re-saving the whole entity, to avoid a read-modify-write race with a concurrent
 * write to the same document.
 */
@Service
public class MultiplayerGamePersistenceService {

    @Autowired
    private MongoTemplate mongoTemplate;

    /**
     * Persists the result of one accepted move (correct or not) and, when it ended the game, the
     * final outcome. Runs on the dedicated {@code multiplayerGameExecutor} pool, never the
     * calling (STOMP handler) thread. {@code move} is null for a timeout, which has no submitted
     * move to record.
     */
    @Async("multiplayerGameExecutor")
    public void persistMove(String gameId, String currentGrid, MultiplayerGameStatus status,
                             PlayerSlot currentTurn, LocalDateTime turnDeadline,
                             MultiplayerGameOutcome outcome, MultiplayerGameEndReason endReason,
                             LocalDateTime endedAt, MultiplayerMove move) {
        Update update = new Update()
                .set("currentGrid", currentGrid)
                .set("status", status)
                .set("currentTurn", currentTurn)
                .set("turnDeadline", turnDeadline)
                .set("outcome", outcome)
                .set("endReason", endReason)
                .set("endedAt", endedAt);
        if (move != null) {
            update.push("moveHistory", move);
        }
        mongoTemplate.updateFirst(Query.query(where("id").is(gameId)), update, MultiplayerGame.class);
    }

    /** Persists a waiting game transitioning to IN_PROGRESS once the second player joins. */
    public void persistGameStarted(String gameId, MultiplayerParticipant player2, MultiplayerGameStatus status,
                                    PlayerSlot currentTurn, LocalDateTime turnDeadline, LocalDateTime startedAt) {
        Update update = new Update()
                .set("player2", player2)
                .set("status", status)
                .set("currentTurn", currentTurn)
                .set("turnDeadline", turnDeadline)
                .set("startedAt", startedAt);
        mongoTemplate.updateFirst(Query.query(where("id").is(gameId)), update, MultiplayerGame.class);
    }
}