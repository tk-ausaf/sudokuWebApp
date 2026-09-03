package com.ausaf.sudoku.multiplayer;

import com.ausaf.sudoku.service.GeneratedMultiplayerPuzzle;
import com.ausaf.sudoku.service.SudokuGeneratorService;
import com.ausaf.sudoku.service.UniqueSolutionSudokuGenerator;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies {@link UniqueSolutionSudokuGenerator} always produces a puzzle with exactly one
 * solution and the requested clue count. A plain unit test - no Spring context or MongoDB
 * needed, since both the generator and {@link SudokuGeneratorService} it wraps are dependency-free.
 */
class UniqueSolutionSudokuGeneratorTest {

    private static final int CELLS_TO_REMOVE = 40;

    private final SudokuGeneratorService generatorService = new SudokuGeneratorService();
    private final UniqueSolutionSudokuGenerator uniqueGenerator = newUniqueGenerator();

    private UniqueSolutionSudokuGenerator newUniqueGenerator() {
        UniqueSolutionSudokuGenerator generator = new UniqueSolutionSudokuGenerator();
        ReflectionTestUtils.setField(generator, "generatorService", generatorService);
        return generator;
    }

    /** Repeated randomized generation: every run must yield exactly one solution and the requested clue count. */
    @RepeatedTest(5)
    void generatedPuzzleHasExactlyOneSolutionAndRequestedClueCount() {
        GeneratedMultiplayerPuzzle puzzle = uniqueGenerator.generate(CELLS_TO_REMOVE);

        assertThat(puzzle.clueGrid()).hasSize(81);
        assertThat(puzzle.solutionGrid()).hasSize(81);

        long blankCount = puzzle.clueGrid().chars().filter(c -> c == '0').count();
        assertThat(blankCount).isEqualTo(CELLS_TO_REMOVE);

        assertThat(countSolutionsIndependently(puzzle.clueGrid())).isEqualTo(1);

        for (int i = 0; i < 81; i++) {
            char clue = puzzle.clueGrid().charAt(i);
            if (clue != '0') {
                assertThat(puzzle.solutionGrid().charAt(i)).isEqualTo(clue);
            }
        }
    }

    /** Independent brute-force solution count (deliberately not reusing the generator's own counter), capped at 2. */
    private int countSolutionsIndependently(String clueGrid) {
        int[][] grid = generatorService.fromStringGrid(clueGrid);
        return count(grid, 0, 0, 2);
    }

    private int count(int[][] grid, int row, int col, int limit) {
        if (row == 9) {
            return 1;
        }
        int nextRow = col == 8 ? row + 1 : row;
        int nextCol = col == 8 ? 0 : col + 1;
        if (grid[row][col] != 0) {
            return count(grid, nextRow, nextCol, limit);
        }
        int found = 0;
        for (int num = 1; num <= 9 && found < limit; num++) {
            if (isValid(grid, row, col, num)) {
                grid[row][col] = num;
                found += count(grid, nextRow, nextCol, limit - found);
                grid[row][col] = 0;
            }
        }
        return found;
    }

    private boolean isValid(int[][] grid, int row, int col, int num) {
        for (int i = 0; i < 9; i++) {
            if (grid[row][i] == num || grid[i][col] == num) {
                return false;
            }
        }
        int boxRow = row - row % 3;
        int boxCol = col - col % 3;
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                if (grid[boxRow + r][boxCol + c] == num) {
                    return false;
                }
            }
        }
        return true;
    }
}