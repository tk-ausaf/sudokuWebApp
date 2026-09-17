package com.ausaf.sudoku.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Holds the entire pre-generated puzzle pool in memory - loaded once from the bundled
 * {@value #PUZZLE_FILE}/{@value #SOLUTION_FILE} classpath resources when this singleton is
 * constructed - and hands out a random one (or, for a client that already displayed a specific
 * bank entry client-side, that exact one) on request. The two files are index-aligned (line N of
 * one is line N's clue grid, line N of the other its solution) rather than one combined file,
 * since {@value #PUZZLE_FILE} alone is also shipped to the frontend for instant, backend-free
 * puzzle display - {@value #SOLUTION_FILE} never leaves the backend. At ~1000 puzzles this is a
 * trivial amount of memory, and serving from it means single-player and multiplayer puzzle
 * assignment never pay the cost of generating a unique-solution puzzle - or even a database round
 * trip - on the request thread. See {@link PuzzleFileGenerator} for how both files are
 * (re)produced offline, and keep the frontend's copy of {@value #PUZZLE_FILE}
 * (`frontend/public/puzzles.txt`) in sync with this one if regenerated.
 */
@Slf4j
@Service
public class PuzzleBankService {

    static final String PUZZLE_FILE = "puzzles.txt";
    static final String SOLUTION_FILE = "solutions.txt";

    private final List<GeneratedMultiplayerPuzzle> puzzles = loadPuzzles(PUZZLE_FILE, SOLUTION_FILE);

    /**
     * @return a uniformly-random puzzle from the in-memory pool, or empty if the pool failed to
     *         load or was empty - callers should fall back to live generation in that case.
     */
    public Optional<GeneratedMultiplayerPuzzle> getRandomPuzzle() {
        if (puzzles.isEmpty()) {
            log.warn("Puzzle pool is empty - caller must fall back to live generation");
            return Optional.empty();
        }
        GeneratedMultiplayerPuzzle puzzle = puzzles.get(ThreadLocalRandom.current().nextInt(puzzles.size()));
        log.debug("Drew a puzzle from the in-memory pool of {}", puzzles.size());
        return Optional.of(puzzle);
    }

    /**
     * @return the pool entry at {@code index} (so a client that already rendered this exact bank
     *         entry from its own bundled copy of {@value #PUZZLE_FILE} can be handed the same
     *         puzzle back, avoiding a visible swap once the backend responds), or empty if the
     *         pool is empty or {@code index} is out of range - callers should fall back to
     *         {@link #getRandomPuzzle()} in that case.
     */
    public Optional<GeneratedMultiplayerPuzzle> getPuzzleAt(int index) {
        if (index < 0 || index >= puzzles.size()) {
            return Optional.empty();
        }
        return Optional.of(puzzles.get(index));
    }

    /** Parses {@code puzzleResource}/{@code solutionResource} (one grid per line, index-aligned) from the classpath. */
    static List<GeneratedMultiplayerPuzzle> loadPuzzles(String puzzleResource, String solutionResource) {
        List<String> clueGrids = readLines(puzzleResource);
        List<String> solutionGrids = readLines(solutionResource);
        if (clueGrids.isEmpty() || solutionGrids.isEmpty()) {
            return List.of();
        }
        if (clueGrids.size() != solutionGrids.size()) {
            log.error("{} has {} lines but {} has {} lines - refusing to pair them up, puzzle pool is empty",
                    puzzleResource, clueGrids.size(), solutionResource, solutionGrids.size());
            return List.of();
        }

        List<GeneratedMultiplayerPuzzle> loaded = new ArrayList<>(clueGrids.size());
        for (int i = 0; i < clueGrids.size(); i++) {
            loaded.add(new GeneratedMultiplayerPuzzle(clueGrids.get(i), solutionGrids.get(i)));
        }
        log.info("Loaded {} puzzles from {}/{} into memory", loaded.size(), puzzleResource, solutionResource);
        return List.copyOf(loaded);
    }

    /** @return every non-blank line of {@code resourceName} from the classpath, or empty if it's missing. */
    private static List<String> readLines(String resourceName) {
        ClassPathResource resource = new ClassPathResource(resourceName);
        if (!resource.exists()) {
            log.warn("{} not found on the classpath - puzzle pool is empty", resourceName);
            return List.of();
        }

        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) {
                    lines.add(line);
                }
            }
        } catch (IOException e) {
            log.error("Failed to read {} from classpath", resourceName, e);
            return List.of();
        }
        return lines;
    }
}