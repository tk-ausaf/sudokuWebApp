package com.ausaf.sudoku.service;

import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Standalone dev tool (not a test - no {@code @Test} methods, never run by the test suite or the
 * app) that (re)generates {@code src/main/resources/puzzles.txt}, the puzzle pool
 * {@link PuzzleBankService} loads entirely into memory at startup instead of generating puzzles
 * live. Run {@link #main} directly (e.g. from the IDE) whenever the pool needs regenerating.
 */
public class PuzzleFileGenerator {

    private static final int PUZZLE_COUNT = 1000;
    private static final int CELLS_TO_REMOVE = 45;
    private static final Path OUTPUT_PATH = Path.of("src", "main", "resources", PuzzleBankService.PUZZLE_FILE);

    public static void main(String[] args) throws IOException {
        SudokuGeneratorService generatorService = new SudokuGeneratorService();
        UniqueSolutionSudokuGenerator uniqueGenerator = new UniqueSolutionSudokuGenerator();
        ReflectionTestUtils.setField(uniqueGenerator, "generatorService", generatorService);

        List<String> lines = new ArrayList<>(PUZZLE_COUNT);
        long startedAtMs = System.currentTimeMillis();
        for (int i = 0; i < PUZZLE_COUNT; i++) {
            GeneratedMultiplayerPuzzle puzzle = uniqueGenerator.generate(CELLS_TO_REMOVE);
            lines.add(puzzle.clueGrid() + "," + puzzle.solutionGrid());
            if ((i + 1) % 100 == 0) {
                System.out.printf("Generated %d/%d puzzles (%d ms elapsed)%n",
                        i + 1, PUZZLE_COUNT, System.currentTimeMillis() - startedAtMs);
            }
        }

        Files.write(OUTPUT_PATH, lines, StandardCharsets.UTF_8);
        System.out.printf("Wrote %d puzzles to %s%n", lines.size(), OUTPUT_PATH.toAbsolutePath());
    }
}