package com.ausaf.sudoku.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Generates, solves, and converts 9x9 Sudoku grids via randomized backtracking. */
@Service
public class SudokuGeneratorService {

    private static final int SIZE = 9;
    private static final int BOX = 3;

    /** Generates a fully solved, randomly-shuffled 9x9 Sudoku grid via backtracking. */
    public int[][] generateSolvedGrid() {
        int[][] grid = new int[SIZE][SIZE];
        fill(grid, 0, 0);
        return grid;
    }

    /** Recursively fills cells in row-major order with a shuffled candidate order, backtracking on dead ends. */
    private boolean fill(int[][] grid, int row, int col) {
        if (row == SIZE) {
            return true;
        }
        int nextRow = col == SIZE - 1 ? row + 1 : row;
        int nextCol = col == SIZE - 1 ? 0 : col + 1;

        List<Integer> candidates = new ArrayList<>(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9));
        Collections.shuffle(candidates);

        for (int num : candidates) {
            if (isValidPlacement(grid, row, col, num)) {
                grid[row][col] = num;
                if (fill(grid, nextRow, nextCol)) {
                    return true;
                }
                grid[row][col] = 0;
            }
        }
        return false;
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

    /** Solves a partially-filled grid via backtracking, treating non-zero cells as fixed givens. */
    public int[][] solve(int[][] clues) {
        int[][] grid = new int[SIZE][SIZE];
        for (int r = 0; r < SIZE; r++) {
            grid[r] = clues[r].clone();
        }
        if (!solveInPlace(grid, 0, 0)) {
            throw new IllegalStateException("Puzzle has no solution");
        }
        return grid;
    }

    /** Recursive backtracking solve used by {@link #solve}: only fills cells that start at zero. */
    private boolean solveInPlace(int[][] grid, int row, int col) {
        if (row == SIZE) {
            return true;
        }
        int nextRow = col == SIZE - 1 ? row + 1 : row;
        int nextCol = col == SIZE - 1 ? 0 : col + 1;

        if (grid[row][col] != 0) {
            return solveInPlace(grid, nextRow, nextCol);
        }

        for (int num = 1; num <= 9; num++) {
            if (isValidPlacement(grid, row, col, num)) {
                grid[row][col] = num;
                if (solveInPlace(grid, nextRow, nextCol)) {
                    return true;
                }
                grid[row][col] = 0;
            }
        }
        return false;
    }

    /** Removes {@code cellsToRemove} random cells from a solved grid to create a puzzle. */
    public int[][] createPuzzle(int[][] solution, int cellsToRemove) {
        int[][] puzzle = new int[SIZE][SIZE];
        for (int r = 0; r < SIZE; r++) {
            puzzle[r] = solution[r].clone();
        }

        List<Integer> positions = new ArrayList<>();
        for (int i = 0; i < SIZE * SIZE; i++) {
            positions.add(i);
        }
        Collections.shuffle(positions);

        for (int i = 0; i < cellsToRemove; i++) {
            int pos = positions.get(i);
            puzzle[pos / SIZE][pos % SIZE] = 0;
        }
        return puzzle;
    }

    /** Flattens a 9x9 grid into an 81-char row-major string. */
    public String toStringGrid(int[][] grid) {
        StringBuilder sb = new StringBuilder(SIZE * SIZE);
        for (int[] row : grid) {
            for (int v : row) {
                sb.append(v);
            }
        }
        return sb.toString();
    }

    /** Parses an 81-char row-major string back into a 9x9 grid. */
    public int[][] fromStringGrid(String s) {
        int[][] grid = new int[SIZE][SIZE];
        for (int i = 0; i < SIZE * SIZE; i++) {
            grid[i / SIZE][i % SIZE] = s.charAt(i) - '0';
        }
        return grid;
    }
}