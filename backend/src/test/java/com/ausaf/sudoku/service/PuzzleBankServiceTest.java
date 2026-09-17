package com.ausaf.sudoku.service;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link PuzzleBankService} - both its classpath-file loading ({@link
 * PuzzleBankService#loadPuzzles}) and its random draw ({@link PuzzleBankService#getRandomPuzzle()}),
 * built without a Spring context or MongoDB since the pool now lives entirely in memory.
 */
class PuzzleBankServiceTest {

    /** Tiny fixture files under {@code src/test/resources}, distinct from the real bundled puzzles.txt/solutions.txt. */
    private static final String FIXTURE_CLUES_FILE = "test-puzzles-fixture-clues.txt";
    private static final String FIXTURE_SOLUTIONS_FILE = "test-puzzles-fixture-solutions.txt";

    @Test
    void loadPuzzlesParsesEveryLineOfTheFixtureFiles() {
        List<GeneratedMultiplayerPuzzle> puzzles = PuzzleBankService.loadPuzzles(FIXTURE_CLUES_FILE, FIXTURE_SOLUTIONS_FILE);

        assertThat(puzzles).hasSize(2);
        assertThat(puzzles.get(0).clueGrid()).isEqualTo("0".repeat(81));
        assertThat(puzzles.get(0).solutionGrid()).isEqualTo("1".repeat(81));
        assertThat(puzzles.get(1).clueGrid()).isEqualTo("2".repeat(81));
        assertThat(puzzles.get(1).solutionGrid()).isEqualTo("3".repeat(81));
    }

    @Test
    void loadPuzzlesReturnsEmptyListWhenEitherResourceIsMissing() {
        assertThat(PuzzleBankService.loadPuzzles("no-such-file.txt", FIXTURE_SOLUTIONS_FILE)).isEmpty();
        assertThat(PuzzleBankService.loadPuzzles(FIXTURE_CLUES_FILE, "no-such-file.txt")).isEmpty();
    }

    @Test
    void loadPuzzlesReturnsEmptyListWhenFilesHaveMismatchedLineCounts() {
        assertThat(PuzzleBankService.loadPuzzles(FIXTURE_CLUES_FILE, "test-puzzles-fixture-mismatched-solutions.txt")).isEmpty();
    }

    @Test
    void getRandomPuzzleReturnsEmptyWhenPoolIsEmpty() {
        PuzzleBankService service = newServiceWithPool(List.of());

        assertThat(service.getRandomPuzzle()).isEmpty();
    }

    @Test
    void getRandomPuzzleAlwaysReturnsOneFromThePool() {
        GeneratedMultiplayerPuzzle only = new GeneratedMultiplayerPuzzle("0".repeat(81), "1".repeat(81));
        PuzzleBankService service = newServiceWithPool(List.of(only));

        for (int i = 0; i < 10; i++) {
            Optional<GeneratedMultiplayerPuzzle> puzzle = service.getRandomPuzzle();
            assertThat(puzzle).contains(only);
        }
    }

    private PuzzleBankService newServiceWithPool(List<GeneratedMultiplayerPuzzle> pool) {
        PuzzleBankService service = new PuzzleBankService();
        ReflectionTestUtils.setField(service, "puzzles", pool);
        return service;
    }
}