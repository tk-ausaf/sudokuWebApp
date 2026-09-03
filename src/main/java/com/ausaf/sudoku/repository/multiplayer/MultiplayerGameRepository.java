package com.ausaf.sudoku.repository.multiplayer;

import com.ausaf.sudoku.entity.MultiplayerGame;
import com.ausaf.sudoku.entity.MultiplayerGameStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/** Spring Data MongoDB repository for {@link MultiplayerGame} documents. */
@Repository
public interface MultiplayerGameRepository extends MongoRepository<MultiplayerGame, String> {

    /**
     * Safety-net query for games whose turn deadline has already passed while still marked
     * in-progress - used to catch a timeout that was lost from memory (e.g. a server restart),
     * since the in-memory scheduled timeout is the primary enforcement mechanism.
     */
    List<MultiplayerGame> findByStatusAndTurnDeadlineBefore(MultiplayerGameStatus status, LocalDateTime cutoff);
}