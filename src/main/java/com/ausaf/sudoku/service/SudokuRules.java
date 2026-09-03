package com.ausaf.sudoku.service;

/**
 * Stateless 9x9 Sudoku placement-legality check shared by {@link SudokuGeneratorService} (which
 * stops backtracking at the first solution) and {@link UniqueSolutionSudokuGenerator} (which
 * needs to keep counting past the first to detect a second one) - the rule itself is identical
 * in both, only what the caller does with it differs.
 */
final class SudokuRules {

    private static final int SIZE = 9;
    private static final int BOX = 3;

    private SudokuRules() {
    }

    /** @return true if {@code num} doesn't already appear in this row, column, or 3x3 box. */
    static boolean isValidPlacement(int[][] grid, int row, int col, int num) {
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