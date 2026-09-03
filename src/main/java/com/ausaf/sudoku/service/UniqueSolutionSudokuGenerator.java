package com.ausaf.sudoku.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Generates Sudoku puzzles for multiplayer games that are guaranteed to have exactly one valid
 * solution, which {@link MultiplayerGameEngine} relies on to judge a submitted digit as
 * correct/incorrect unambiguously. Reuses {@link SudokuGeneratorService#generateSolvedGrid()} and
 * its grid/string conversion helpers read-only; deliberately does not reuse
 * {@link SudokuGeneratorService#solve} since that method stops at the first solution found and
 * has no way to detect a second one - single-player puzzle generation is unaffected by this class.
 */
@Service
public class UniqueSolutionSudokuGenerator {

    private static final int SIZE = 9;
    private static final int BOX = 3;

    @Autowired
    private SudokuGeneratorService generatorService;

    /**
     * Generates a solved grid, then removes {@code cellsToRemove} clues one at a time, keeping
     * each removal only if the puzzle still has exactly one solution afterward - so the
     * uniqueness invariant holds after every step, not just at the end.
     */
    public GeneratedMultiplayerPuzzle generate(int cellsToRemove) {
        int[][] solved = generatorService.generateSolvedGrid();
        int[][] puzzle = createUniquePuzzle(solved, cellsToRemove);
        return new GeneratedMultiplayerPuzzle(generatorService.toStringGrid(puzzle), generatorService.toStringGrid(solved));
    }

    /** @return a copy of {@code solved} with up to {@code cellsToRemove} clues blanked, each removal solution-preserving. */
    int[][] createUniquePuzzle(int[][] solved, int cellsToRemove) {
        int[][] puzzle = new int[SIZE][SIZE];
        for (int r = 0; r < SIZE; r++) {
            puzzle[r] = solved[r].clone();
        }

        List<Integer> positions = new ArrayList<>();
        for (int i = 0; i < SIZE * SIZE; i++) {
            positions.add(i);
        }
        Collections.shuffle(positions);

        int removed = 0;
        for (int pos : positions) {
            if (removed >= cellsToRemove) {
                break;
            }
            int row = pos / SIZE;
            int col = pos % SIZE;
            int previousValue = puzzle[row][col];
            puzzle[row][col] = 0;

            if (countSolutions(puzzle, 2) == 1) {
                removed++;
            } else {
                puzzle[row][col] = previousValue;
            }
        }
        return puzzle;
    }

    /** Counts solutions to {@code grid} via backtracking, stopping early the moment {@code limit} is reached. */
    private int countSolutions(int[][] grid, int limit) {
        return countFrom(grid, 0, 0, limit);
    }

    /** Recursive counting backtrack: returns as soon as the running count reaches {@code limit}. */
    private int countFrom(int[][] grid, int row, int col, int limit) {
        if (row == SIZE) {
            return 1;
        }
        int nextRow = col == SIZE - 1 ? row + 1 : row;
        int nextCol = col == SIZE - 1 ? 0 : col + 1;

        if (grid[row][col] != 0) {
            return countFrom(grid, nextRow, nextCol, limit);
        }

        int found = 0;
        for (int num = 1; num <= 9 && found < limit; num++) {
            if (isValidPlacement(grid, row, col, num)) {
                grid[row][col] = num;
                found += countFrom(grid, nextRow, nextCol, limit - found);
                grid[row][col] = 0;
            }
        }
        return found;
    }

    /** @return true if {@code num} doesn't already appear in this row, column, or 3x3 box. */
    private boolean isValidPlacement(int[][] grid, int row, int col, int num) {
        for (int i = 0; i < SIZE; i++) {
            if (grid[row][i] == num || grid[i][col] == num) {
                return false;
            }
        }
        int boxRow = row - row % BOX;
        int boxCol = col - col % BOX;
        for (int r = 0; r < BOX; r++) {
            for (int c = 0; c < BOX; c++) {
                if (grid[boxRow + r][boxCol + c] == num) {
                    return false;
                }
            }
        }
        return true;
    }
}
