package com.ausaf.sudoku.repository.attempt;

import com.ausaf.sudoku.entity.PuzzleAttempt;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PuzzleAttemptRepository extends MongoRepository<PuzzleAttempt, String> {
    List<PuzzleAttempt> findByUserId(String userId);
    Optional<PuzzleAttempt> findFirstByUserIdAndCompletedFalse(String userId);
}