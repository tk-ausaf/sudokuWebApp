package com.ausaf.sudoku.service;

import com.ausaf.sudoku.entity.Puzzle;
import com.ausaf.sudoku.repository.puzzle.PuzzleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** Seeds the puzzle collection with 1000 puzzles on startup, if not already present. */
@Component
public class PuzzleSeedService implements ApplicationRunner {

    private static final int TARGET_COUNT = 1000;
    private static final int CELLS_TO_REMOVE = 45;
    private static final int BATCH_SIZE = 100;

    @Autowired
    private PuzzleRepository puzzleRepository;

    @Autowired
    private SudokuGeneratorService generatorService;

    @Override
    public void run(ApplicationArguments args) {
        long existing = puzzleRepository.count();
        if (existing >= TARGET_COUNT) {
            return;
        }

        List<Puzzle> batch = new ArrayList<>(BATCH_SIZE);
        for (long i = existing; i < TARGET_COUNT; i++) {
            int[][] solution = generatorService.generateSolvedGrid();
            int[][] puzzleGrid = generatorService.createPuzzle(solution, CELLS_TO_REMOVE);

            Puzzle puzzle = new Puzzle();
            puzzle.setIndex((int) (i + 1));
            puzzle.setPuzzle(generatorService.toStringGrid(puzzleGrid));
            puzzle.setSolution(generatorService.toStringGrid(solution));
            batch.add(puzzle);

            if (batch.size() == BATCH_SIZE) {
                puzzleRepository.saveAll(batch);
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            puzzleRepository.saveAll(batch);
        }
    }
}