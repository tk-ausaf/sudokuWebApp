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
 * {@value #PUZZLE_FILE} classpath resource when this singleton is constructed - and hands out a
 * random one on request. At ~1000 puzzles this is a trivial amount of memory, and serving from it
 * means single-player and multiplayer puzzle assignment never pay the cost of generating a
 * unique-solution puzzle - or even a database round trip - on the request thread. See
 * {@link PuzzleFileGenerator} for how {@value #PUZZLE_FILE} is (re)produced offline.
 */
@Slf4j
@Service
public class PuzzleBankService {

    static final String PUZZLE_FILE = "puzzles.txt";

    private final List<GeneratedMultiplayerPuzzle> puzzles = loadPuzzles(PUZZLE_FILE);

    /**
     * @return a uniformly-random puzzle from the in-memory pool, or empty if {@value #PUZZLE_FILE}
     *         failed to load or was empty - callers should fall back to live generation in that case.
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

    /** Parses {@code resourceName} (one {@code clueGrid,solutionGrid} pair per line) from the classpath. */
    static List<GeneratedMultiplayerPuzzle> loadPuzzles(String resourceName) {
        ClassPathResource resource = new ClassPathResource(resourceName);
        if (!resource.exists()) {
            log.warn("{} not found on the classpath - puzzle pool is empty", resourceName);
            return List.of();
        }

        List<GeneratedMultiplayerPuzzle> loaded = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                String[] parts = line.split(",", 2);
                loaded.add(new GeneratedMultiplayerPuzzle(parts[0], parts[1]));
            }
        } catch (IOException e) {
            log.error("Failed to read {} from classpath", resourceName, e);
            return List.of();
        }
        log.info("Loaded {} puzzles from {} into memory", loaded.size(), resourceName);
        return List.copyOf(loaded);
    }
}