package com.ausaf.sudoku.service;

import com.ausaf.sudoku.dto.AttemptSummary;
import com.ausaf.sudoku.dto.PuzzleResponse;
import com.ausaf.sudoku.dto.ResumeResponse;
import com.ausaf.sudoku.dto.SubmitResponse;
import com.ausaf.sudoku.entity.PuzzleAttempt;
import com.ausaf.sudoku.repository.attempt.PuzzleAttemptRepository;
import com.ausaf.sudoku.security.CallerIdentity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Core sudoku gameplay: assigning/generating a puzzle, validating and recording a submitted
 * solution, live autosave of in-progress cells, and the resume/history list - for both guest
 * and logged-in callers, via {@link IdentityResolver}.
 */
@Service
public class SudokuService {

    private static final int SIZE = 9;
    private static final int CELLS_TO_REMOVE = 45;

    @Autowired
    private PuzzleAttemptRepository attemptRepository;

    @Autowired
    private SudokuGeneratorService generatorService;

    @Autowired
    private IdentityResolver identityResolver;

    /** Resumes the caller's one in-progress attempt, or generates a brand new puzzle on the spot. */
    public PuzzleResponse getPuzzleForUser(CallerIdentity identity) {
        ResolvedIdentity owner = identityResolver.resolve(identity);

        // .filter(...) guards against resuming a pre-migration attempt document that lacks a
        // clueGrid (e.g. created under an older schema) - such a document is unusable, so fall
        // through and generate a fresh one instead of handing the client a null clue grid.
        Optional<PuzzleAttempt> active = (owner.isUser()
                ? attemptRepository.findFirstByUserIdAndCompletedFalse(owner.getUserId())
                : attemptRepository.findFirstByAnonymousIdAndCompletedFalse(owner.getAnonymousId()))
                .filter(a -> a.getClueGrid() != null);

        if (active.isPresent()) {
            return toResponse(active.get());
        }

        int[][] solved = generatorService.generateSolvedGrid();
        int[][] puzzleGrid = generatorService.createPuzzle(solved, CELLS_TO_REMOVE);
        String clueGrid = generatorService.toStringGrid(puzzleGrid);

        PuzzleAttempt attempt = new PuzzleAttempt();
        owner.applyAsOwner(attempt);
        attempt.setClueGrid(clueGrid);
        attempt.setCompleted(false);
        attempt.setAssignedAt(LocalDateTime.now());
        attemptRepository.save(attempt);

        return toResponse(attempt);
    }

    /**
     * Validates a proposed solution against the attempt's clues and Sudoku rules, and marks it
     * completed if correct. Never disqualifies or resets the clock on a wrong guess.
     */
    public SubmitResponse submitSolution(CallerIdentity identity, String attemptId, String grid) {
        ResolvedIdentity owner = identityResolver.resolve(identity);

        PuzzleAttempt attempt = attemptRepository.findById(attemptId)
                .filter(owner::owns)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Attempt not found"));

        if (attempt.isCompleted()) {
            return new SubmitResponse(true, "Already completed");
        }

        if (grid == null || grid.length() != SIZE * SIZE || !grid.chars().allMatch(c -> c >= '1' && c <= '9')) {
            return new SubmitResponse(false, "Grid must contain 81 digits, each from 1-9");
        }

        if (!cluesMatch(attempt.getClueGrid(), grid)) {
            return new SubmitResponse(false, "Submitted grid changes one of the given numbers");
        }

        if (!isValidSolvedGrid(grid)) {
            return new SubmitResponse(false, "Grid is not a valid Sudoku solution");
        }

        // Wrong submissions never reach here (they return early above) and never disqualify or
        // reset the clock - assignedAt is untouched, so elapsed time is a continuous clock from
        // true first-view to this first fully-correct submit.
        attempt.setCompleted(true);
        attempt.setCompletedAt(LocalDateTime.now());
        attempt.setCurrentGrid(grid);
        attemptRepository.save(attempt);
        return new SubmitResponse(true, "Done! Puzzle solved correctly.");
    }

    /** Live autosave of in-progress cell values, so an attempt can be resumed exactly where left off. */
    public void autosaveGrid(CallerIdentity identity, String attemptId, String grid) {
        ResolvedIdentity owner = identityResolver.resolve(identity);

        PuzzleAttempt attempt = attemptRepository.findById(attemptId)
                .filter(owner::owns)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Attempt not found"));

        if (attempt.isCompleted()) {
            return;
        }

        if (grid == null || grid.length() != SIZE * SIZE || !grid.chars().allMatch(c -> c >= '0' && c <= '9')) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Grid must be 81 chars, digits 0-9");
        }

        if (!cluesMatch(attempt.getClueGrid(), grid)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Autosaved grid changes one of the given numbers");
        }

        attempt.setCurrentGrid(grid);
        attempt.setLastSavedAt(LocalDateTime.now());
        attemptRepository.save(attempt);
    }

    /** Resume/history list: most recent first, no grid payload (kept small). */
    public List<AttemptSummary> getHistory(CallerIdentity identity) {
        ResolvedIdentity owner = identityResolver.resolve(identity);
        List<PuzzleAttempt> attempts = owner.isUser()
                ? attemptRepository.findByUserIdOrderByAssignedAtDesc(owner.getUserId())
                : attemptRepository.findByAnonymousIdOrderByAssignedAtDesc(owner.getAnonymousId());

        return attempts.stream()
                .map(a -> new AttemptSummary(
                        a.getId(), a.isCompleted(), a.getAssignedAt(), a.getCompletedAt(),
                        a.getCurrentGrid() != null))
                .toList();
    }

    /** @return the clue grid plus latest saved progress for one specific owned attempt. */
    public ResumeResponse resumeAttempt(CallerIdentity identity, String attemptId) {
        ResolvedIdentity owner = identityResolver.resolve(identity);

        PuzzleAttempt attempt = attemptRepository.findById(attemptId)
                .filter(owner::owns)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Attempt not found"));

        String currentGrid = attempt.getCurrentGrid() != null ? attempt.getCurrentGrid() : attempt.getClueGrid();

        return new ResumeResponse(attempt.getId(), attempt.getClueGrid(), currentGrid, attempt.isCompleted());
    }

    /** @return true if {@code grid} keeps every given-clue cell from {@code clues} unchanged. */
    private boolean cluesMatch(String clues, String grid) {
        for (int i = 0; i < SIZE * SIZE; i++) {
            char clue = clues.charAt(i);
            if (clue != '0' && clue != grid.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    /** @return true if every row, column, and 3x3 box of {@code grid} is a permutation of 1-9. */
    private boolean isValidSolvedGrid(String grid) {
        int[][] g = generatorService.fromStringGrid(grid);

        for (int i = 0; i < SIZE; i++) {
            if (!isOneToNine(rowOf(g, i)) || !isOneToNine(colOf(g, i))) {
                return false;
            }
        }
        for (int boxRow = 0; boxRow < SIZE; boxRow += 3) {
            for (int boxCol = 0; boxCol < SIZE; boxCol += 3) {
                if (!isOneToNine(boxOf(g, boxRow, boxCol))) {
                    return false;
                }
            }
        }
        return true;
    }

    /** @return a copy of one full row. */
    private int[] rowOf(int[][] g, int row) {
        return g[row].clone();
    }

    /** @return the values down one full column. */
    private int[] colOf(int[][] g, int col) {
        int[] values = new int[SIZE];
        for (int row = 0; row < SIZE; row++) {
            values[row] = g[row][col];
        }
        return values;
    }

    /** @return the 9 values of the 3x3 box whose top-left corner is ({@code boxRow}, {@code boxCol}). */
    private int[] boxOf(int[][] g, int boxRow, int boxCol) {
        int[] values = new int[SIZE];
        int idx = 0;
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                values[idx++] = g[boxRow + r][boxCol + c];
            }
        }
        return values;
    }

    /** @return true if {@code values} contains each of 1-9 exactly once. */
    private boolean isOneToNine(int[] values) {
        boolean[] seen = new boolean[10];
        for (int v : values) {
            if (v < 1 || v > 9 || seen[v]) {
                return false;
            }
            seen[v] = true;
        }
        return true;
    }

    /** Builds the client-facing response for an attempt, defaulting currentGrid to the clue grid if unsaved. */
    private PuzzleResponse toResponse(PuzzleAttempt attempt) {
        String currentGrid = attempt.getCurrentGrid() != null ? attempt.getCurrentGrid() : attempt.getClueGrid();
        return new PuzzleResponse(attempt.getId(), attempt.getClueGrid(), currentGrid);
    }
}