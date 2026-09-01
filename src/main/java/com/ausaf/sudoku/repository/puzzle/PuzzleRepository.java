package com.ausaf.sudoku.repository.puzzle;

import com.ausaf.sudoku.entity.Puzzle;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PuzzleRepository extends MongoRepository<Puzzle, String> {
}