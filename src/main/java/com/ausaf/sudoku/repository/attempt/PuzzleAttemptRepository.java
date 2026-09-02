package com.ausaf.sudoku.repository.attempt;

import com.ausaf.sudoku.entity.PuzzleAttempt;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PuzzleAttemptRepository extends MongoRepository<PuzzleAttempt, String> {

    // Resume the one in-progress attempt, if any.
    Optional<PuzzleAttempt> findFirstByUserIdAndCompletedFalse(String userId);
    Optional<PuzzleAttempt> findFirstByAnonymousIdAndCompletedFalse(String anonymousId);

    // History / resume list, most recent first.
    List<PuzzleAttempt> findByUserIdOrderByAssignedAtDesc(String userId);
    List<PuzzleAttempt> findByAnonymousIdOrderByAssignedAtDesc(String anonymousId);

    // Merge-on-login: every attempt a guest identity has ever touched.
    List<PuzzleAttempt> findByAnonymousId(String anonymousId);
}
