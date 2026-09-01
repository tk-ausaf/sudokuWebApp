package com.ausaf.sudoku.service;

import com.ausaf.sudoku.dto.PuzzleResponse;
import com.ausaf.sudoku.dto.SubmitResponse;
import com.ausaf.sudoku.entity.Puzzle;
import com.ausaf.sudoku.entity.PuzzleAttempt;
import com.ausaf.sudoku.entity.User;
import com.ausaf.sudoku.repository.attempt.PuzzleAttemptRepository;
import com.ausaf.sudoku.repository.puzzle.PuzzleRepository;
import com.ausaf.sudoku.repository.user.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Random;

@Service
public class SudokuService {

    private static final int SIZE = 9;

    @Autowired
    private PuzzleRepository puzzleRepository;

    @Autowired
    private PuzzleAttemptRepository attemptRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private SudokuGeneratorService generatorService;

    private final Random random = new Random();

    public PuzzleResponse getPuzzleForUser(String username) {
        User user = findUser(username);

        Optional<PuzzleAttempt> active = attemptRepository.findFirstByUserIdAndCompletedFalse(user.getId());
        if (active.isPresent()) {
            Puzzle puzzle = findPuzzle(active.get().getPuzzleId());
            return toResponse(active.get(), puzzle);
        }

        List<String> excludedPuzzleIds = attemptRepository.findByUserId(user.getId()).stream()
                .map(PuzzleAttempt::getPuzzleId)
                .toList();

        Query query = new Query(Criteria.where("id").nin(excludedPuzzleIds));
        List<Puzzle> candidates = mongoTemplate.find(query, Puzzle.class);
        if (candidates.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No new puzzles left to assign");
        }
        Puzzle puzzle = candidates.get(random.nextInt(candidates.size()));

        PuzzleAttempt attempt = new PuzzleAttempt();
        attempt.setUserId(user.getId());
        attempt.setPuzzleId(puzzle.getId());
        attempt.setCompleted(false);
        attempt.setAssignedAt(LocalDateTime.now());
        attemptRepository.save(attempt);

        return toResponse(attempt, puzzle);
    }

    public SubmitResponse submitSolution(String username, String attemptId, String grid) {
        User user = findUser(username);

        PuzzleAttempt attempt = attemptRepository.findById(attemptId)
                .filter(a -> a.getUserId().equals(user.getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Attempt not found"));

        if (attempt.isCompleted()) {
            return new SubmitResponse(true, "Already completed");
        }

        Puzzle puzzle = findPuzzle(attempt.getPuzzleId());

        if (grid == null || grid.length() != SIZE * SIZE || !grid.chars().allMatch(c -> c >= '1' && c <= '9')) {
            return new SubmitResponse(false, "Grid must contain 81 digits, each from 1-9");
        }

        String clues = puzzle.getPuzzle();
        for (int i = 0; i < SIZE * SIZE; i++) {
            char clue = clues.charAt(i);
            if (clue != '0' && clue != grid.charAt(i)) {
                return new SubmitResponse(false, "Submitted grid changes one of the given numbers");
            }
        }

        if (!isValidSolvedGrid(grid)) {
            return new SubmitResponse(false, "Grid is not a valid Sudoku solution");
        }

        attempt.setCompleted(true);
        attempt.setCompletedAt(LocalDateTime.now());
        attemptRepository.save(attempt);
        return new SubmitResponse(true, "Done! Puzzle solved correctly.");
    }

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

    private int[] rowOf(int[][] g, int row) {
        return g[row].clone();
    }

    private int[] colOf(int[][] g, int col) {
        int[] values = new int[SIZE];
        for (int row = 0; row < SIZE; row++) {
            values[row] = g[row][col];
        }
        return values;
    }

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

    private User findUser(String username) {
        User user = userRepository.findByName(username);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found");
        }
        return user;
    }

    private Puzzle findPuzzle(String puzzleId) {
        return puzzleRepository.findById(puzzleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Puzzle not found"));
    }

    private PuzzleResponse toResponse(PuzzleAttempt attempt, Puzzle puzzle) {
        return new PuzzleResponse(attempt.getId(), puzzle.getId(), puzzle.getPuzzle());
    }
}