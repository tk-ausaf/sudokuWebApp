package com.ausaf.sudoku.repository.attempt;

import com.ausaf.sudoku.entity.PuzzleAttempt;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** Spring Data MongoDB repository for {@link PuzzleAttempt} documents. */
@Repository
public interface PuzzleAttemptRepository extends MongoRepository<PuzzleAttempt, String> {

    // Resume the one active (not completed/failed/abandoned) attempt, if any.
    /** @return this user's active attempt, if any (there should be at most one at a time). */
    Optional<PuzzleAttempt> findFirstByUserIdAndCompletedFalseAndFailedFalseAndAbandonedFalse(String userId);
    /** @return this guest's active attempt, if any (there should be at most one at a time). */
    Optional<PuzzleAttempt> findFirstByAnonymousIdAndCompletedFalseAndFailedFalseAndAbandonedFalse(String anonymousId);

    // History / resume list, most recent first.
    /** @return all of this user's attempts, most recently assigned first. */
    List<PuzzleAttempt> findByUserIdOrderByAssignedAtDesc(String userId);
    /** @return all of this guest's attempts, most recently assigned first. */
    List<PuzzleAttempt> findByAnonymousIdOrderByAssignedAtDesc(String anonymousId);

    // Merge-on-login: every attempt a guest identity has ever touched.
    /** @return every attempt (complete or not) ever created under this anonymous session id. */
    List<PuzzleAttempt> findByAnonymousId(String anonymousId);
}
