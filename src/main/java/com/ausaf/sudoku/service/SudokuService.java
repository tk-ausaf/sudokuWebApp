package com.ausaf.sudoku.service;

import com.ausaf.sudoku.dto.AttemptSummary;
import com.ausaf.sudoku.dto.PuzzleResponse;
import com.ausaf.sudoku.dto.ResumeResponse;
import com.ausaf.sudoku.dto.SubmitResponse;
import com.ausaf.sudoku.entity.PuzzleAttempt;
import com.ausaf.sudoku.repository.attempt.PuzzleAttemptRepository;
import com.ausaf.sudoku.security.CallerIdentity;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
@Service
public class SudokuService {

    private static final int SIZE = 9;
    private static final int CELLS_TO_REMOVE = 45;
    private static final int MAX_WRONG_ATTEMPTS = 5;

    @Autowired
    private PuzzleAttemptRepository attemptRepository;

    @Autowired
    private SudokuGeneratorService generatorService;

    @Autowired
    private PuzzleBankService puzzleBankService;

    @Autowired
    private IdentityResolver identityResolver;

    /** Resumes the caller's one in-progress attempt, or generates a brand new puzzle on the spot. */
    public PuzzleResponse getPuzzleForUser(CallerIdentity identity) {
        ResolvedIdentity owner = identityResolver.resolve(identity);

        // .filter(...) guards against resuming a pre-migration attempt document that lacks a
        // clueGrid (e.g. created under an older schema) - such a document is unusable, so fall
        // through and generate a fresh one instead of handing the client a null clue grid.
        Optional<PuzzleAttempt> active = (owner.isUser()
                ? attemptRepository.findFirstByUserIdAndCompletedFalseAndFailedFalseAndAbandonedFalse(owner.getUserId())
                : attemptRepository.findFirstByAnonymousIdAndCompletedFalseAndFailedFalseAndAbandonedFalse(owner.getAnonymousId()))
                .filter(a -> a.getClueGrid() != null);

        if (active.isPresent()) {
            log.debug("Resuming in-progress attempt {} for {}", active.get().getId(), owner.toLogString());
            return toResponse(active.get());
        }

        String clueGrid = puzzleBankService.getRandomPuzzle().map(GeneratedMultiplayerPuzzle::clueGrid)
                .orElseGet(() -> {
                    log.warn("Puzzle bank empty - falling back to live generation for {}", owner.toLogString());
                    int[][] solved = generatorService.generateSolvedGrid();
                    int[][] puzzleGrid = generatorService.createPuzzle(solved, CELLS_TO_REMOVE);
                    return generatorService.toStringGrid(puzzleGrid);
                });

        PuzzleAttempt attempt = new PuzzleAttempt();
        owner.applyAsOwner(attempt);
        attempt.setClueGrid(clueGrid);
        attempt.setCompleted(false);
        attempt.setAssignedAt(LocalDateTime.now());
        attemptRepository.save(attempt);

        log.info("Assigned new attempt {} to {}", attempt.getId(), owner.toLogString());
        return toResponse(attempt);
    }

    /**
     * Validates a proposed solution against the attempt's clues and Sudoku rules, and marks it
     * completed if correct. A genuinely wrong (but well-formed) guess counts toward
     * {@link #MAX_WRONG_ATTEMPTS}; reaching the cap fails the attempt and locks it. Never resets
     * the clock on a wrong guess.
     */
    public SubmitResponse submitSolution(CallerIdentity identity, String attemptId, String grid) {
        ResolvedIdentity owner = identityResolver.resolve(identity);

        PuzzleAttempt attempt = attemptRepository.findById(attemptId)
                .filter(owner::owns)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Attempt not found"));

        if (attempt.isCompleted()) {
            log.debug("Submit for already-completed attempt {} by {}", attemptId, owner.toLogString());
            return submitResponse(attempt, true, "Already completed");
        }

        if (attempt.isFailed() || attempt.isAbandoned()) {
            log.warn("Submit for locked (failed/abandoned) attempt {} by {}", attemptId, owner.toLogString());
            return submitResponse(attempt, false, "This puzzle is locked - start a new one.");
        }

        if (grid == null || grid.length() != SIZE * SIZE || !grid.chars().allMatch(c -> c >= '1' && c <= '9')) {
            log.warn("Malformed submit grid for attempt {} by {}", attemptId, owner.toLogString());
            return submitResponse(attempt, false, "Grid must contain 81 digits, each from 1-9");
        }

        if (!cluesMatch(attempt.getClueGrid(), grid)) {
            log.warn("Submit for attempt {} by {} changed a given clue", attemptId, owner.toLogString());
            return submitResponse(attempt, false, "Submitted grid changes one of the given numbers");
        }

        if (!isValidSolvedGrid(grid)) {
            attempt.setWrongAttempts(attempt.getWrongAttempts() + 1);
            if (attempt.getWrongAttempts() >= MAX_WRONG_ATTEMPTS) {
                attempt.setFailed(true);
                attemptRepository.save(attempt);
                log.info("Attempt {} failed after {} wrong attempts by {}", attemptId, attempt.getWrongAttempts(), owner.toLogString());
                return submitResponse(attempt, false, "No attempts left - puzzle failed. Start a new one.");
            }
            attemptRepository.save(attempt);
            log.info("Incorrect submit for attempt {} by {} ({} of {} wrong attempts used)",
                    attemptId, owner.toLogString(), attempt.getWrongAttempts(), MAX_WRONG_ATTEMPTS);
            return submitResponse(attempt, false, "Grid is not a valid Sudoku solution");
        }

        // Wrong submissions never reach here (they return early above) and never disqualify or
        // reset the clock - assignedAt is untouched, so elapsed time is a continuous clock from
        // true first-view to this first fully-correct submit.
        attempt.setCompleted(true);
        attempt.setCompletedAt(LocalDateTime.now());
        attempt.setCurrentGrid(grid);
        attemptRepository.save(attempt);
        log.info("Attempt {} solved correctly by {}", attemptId, owner.toLogString());
        return submitResponse(attempt, true, "Done! Puzzle solved correctly.");
    }

    /** Builds a {@link SubmitResponse}, always including the attempt's current wrong-attempt/failed state. */
    private SubmitResponse submitResponse(PuzzleAttempt attempt, boolean correct, String message) {
        return new SubmitResponse(correct, message, attempt.getWrongAttempts(), MAX_WRONG_ATTEMPTS, attempt.isFailed());
    }

    /**
     * Saves in-progress cell values (manual "Save Progress" or the opt-in autosave toggle) so an
     * attempt can be resumed exactly where left off. {@code name}, if non-blank, sets the
     * player-chosen label the first time it's provided; a blank/null name leaves any existing
     * label untouched (so the silent every-5-moves autosave never clears it).
     */
    public void autosaveGrid(CallerIdentity identity, String attemptId, String grid, String name) {
        ResolvedIdentity owner = identityResolver.resolve(identity);

        PuzzleAttempt attempt = attemptRepository.findById(attemptId)
                .filter(owner::owns)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Attempt not found"));

        if (attempt.isCompleted() || attempt.isFailed() || attempt.isAbandoned()) {
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
        if (name != null && !name.isBlank()) {
            attempt.setName(name.trim());
        }
        attemptRepository.save(attempt);
        log.debug("Autosaved attempt {} for {}{}", attemptId, owner.toLogString(),
                attempt.getName() != null ? " (named \"" + attempt.getName() + "\")" : "");
    }

    /** Abandons the caller's in-progress attempt (e.g. via "New puzzle") - a no-op if it's already terminal. */
    public void abandonAttempt(CallerIdentity identity, String attemptId) {
        ResolvedIdentity owner = identityResolver.resolve(identity);

        PuzzleAttempt attempt = attemptRepository.findById(attemptId)
                .filter(owner::owns)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Attempt not found"));

        if (attempt.isCompleted() || attempt.isFailed() || attempt.isAbandoned()) {
            log.debug("Abandon requested for already-terminal attempt {} by {}", attemptId, owner.toLogString());
            return;
        }

        attempt.setAbandoned(true);
        attemptRepository.save(attempt);
        log.info("Attempt {} abandoned by {}", attemptId, owner.toLogString());
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
                        a.getCurrentGrid() != null, a.isFailed(), a.isAbandoned(), a.getName()))
                .toList();
    }

    /** @return the clue grid plus latest saved progress for one specific owned attempt. */
    public ResumeResponse resumeAttempt(CallerIdentity identity, String attemptId) {
        ResolvedIdentity owner = identityResolver.resolve(identity);

        PuzzleAttempt attempt = attemptRepository.findById(attemptId)
                .filter(owner::owns)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Attempt not found"));

        String currentGrid = attempt.getCurrentGrid() != null ? attempt.getCurrentGrid() : attempt.getClueGrid();

        return new ResumeResponse(attempt.getId(), attempt.getClueGrid(), currentGrid, attempt.isCompleted(),
                attempt.getWrongAttempts(), MAX_WRONG_ATTEMPTS, attempt.isFailed(), attempt.isAbandoned(), attempt.getName());
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
        return new PuzzleResponse(attempt.getId(), attempt.getClueGrid(), currentGrid,
                attempt.getWrongAttempts(), MAX_WRONG_ATTEMPTS, attempt.isFailed(), attempt.isAbandoned(), attempt.getName());
    }
}