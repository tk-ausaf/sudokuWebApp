package com.ausaf.sudoku;

import com.ausaf.sudoku.dto.AutosaveRequest;
import com.ausaf.sudoku.dto.LeaderboardEntry;
import com.ausaf.sudoku.dto.PuzzleResponse;
import com.ausaf.sudoku.dto.SubmitRequest;
import com.ausaf.sudoku.dto.SubmitResponse;
import com.ausaf.sudoku.entity.PuzzleAttempt;
import com.ausaf.sudoku.entity.User;
import com.ausaf.sudoku.repository.attempt.PuzzleAttemptRepository;
import com.ausaf.sudoku.repository.user.UserRepository;
import com.ausaf.sudoku.service.SudokuGeneratorService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end verification of the guest-play, resume, on-demand puzzle generation, and
 * count-based leaderboard features, exercised against a real (locally running) MongoDB instance.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SudokuFeatureIntegrationTest {

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private PuzzleAttemptRepository attemptRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SudokuGeneratorService generatorService;

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    @Test
    void guestCanFetchAndResumeAnOnDemandPuzzle() {
        ResponseEntity<PuzzleResponse> first = restTemplate.getForEntity(baseUrl() + "/sudoku/puzzle", PuzzleResponse.class);
        assertThat(first.getStatusCode().is2xxSuccessful()).isTrue();

        String setCookie = first.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).contains("sudoku_guest=");
        String cookieValue = setCookie.split(";")[0];

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, cookieValue);
        ResponseEntity<PuzzleResponse> second = restTemplate.exchange(
                baseUrl() + "/sudoku/puzzle", HttpMethod.GET, new HttpEntity<>(headers), PuzzleResponse.class);

        // Same cookie -> resumes the SAME in-progress attempt, not a new one.
        assertThat(second.getBody().getAttemptId()).isEqualTo(first.getBody().getAttemptId());

        PuzzleAttempt stored = attemptRepository.findById(first.getBody().getAttemptId()).orElseThrow();
        assertThat(stored.getUserId()).isNull();
        assertThat(stored.getAnonymousId()).isNotNull();
        assertThat(stored.getClueGrid()).hasSize(81);
    }

    @Test
    void twoIndependentGuestsGetDifferentOnDemandPuzzles() {
        ResponseEntity<PuzzleResponse> a = restTemplate.getForEntity(baseUrl() + "/sudoku/puzzle", PuzzleResponse.class);
        ResponseEntity<PuzzleResponse> b = restTemplate.getForEntity(baseUrl() + "/sudoku/puzzle", PuzzleResponse.class);

        // No cookie reuse -> two distinct guest identities, each with their own freshly
        // generated puzzle (no shared pool, unlike the old daily-puzzle design).
        assertThat(a.getBody().getAttemptId()).isNotEqualTo(b.getBody().getAttemptId());
        assertThat(a.getBody().getClueGrid()).isNotEqualTo(b.getBody().getClueGrid());
    }

    @Test
    void guestAutosaveThenLoginMergesAttemptAndPreservesStartTime() {
        ResponseEntity<PuzzleResponse> puzzleResp = restTemplate.getForEntity(baseUrl() + "/sudoku/puzzle", PuzzleResponse.class);
        String cookieValue = puzzleResp.getHeaders().getFirst(HttpHeaders.SET_COOKIE).split(";")[0];
        String attemptId = puzzleResp.getBody().getAttemptId();

        LocalDateTime originalAssignedAt = attemptRepository.findById(attemptId).orElseThrow().getAssignedAt();

        HttpHeaders saveHeaders = new HttpHeaders();
        saveHeaders.add(HttpHeaders.COOKIE, cookieValue);
        saveHeaders.setContentType(MediaType.APPLICATION_JSON);
        AutosaveRequest autosaveRequest = new AutosaveRequest();
        autosaveRequest.setGrid(puzzleResp.getBody().getCurrentGrid());
        ResponseEntity<Void> saveResp = restTemplate.exchange(
                baseUrl() + "/sudoku/attempts/" + attemptId + "/grid", HttpMethod.PATCH,
                new HttpEntity<>(autosaveRequest, saveHeaders), Void.class);
        assertThat(saveResp.getStatusCode().value()).isEqualTo(204);
        assertThat(attemptRepository.findById(attemptId).orElseThrow().getLastSavedAt()).isNotNull();

        String uniqueName = "itest_" + UUID.randomUUID();
        User newUser = new User(uniqueName, "password123");
        HttpHeaders jsonHeaders = new HttpHeaders();
        jsonHeaders.setContentType(MediaType.APPLICATION_JSON);
        restTemplate.postForEntity(baseUrl() + "/users/addUser", new HttpEntity<>(newUser, jsonHeaders), Boolean.class);

        HttpHeaders signInHeaders = new HttpHeaders();
        signInHeaders.setContentType(MediaType.APPLICATION_JSON);
        signInHeaders.add(HttpHeaders.COOKIE, cookieValue);
        ResponseEntity<String> signInResp = restTemplate.exchange(
                baseUrl() + "/users/signIn", HttpMethod.POST, new HttpEntity<>(newUser, signInHeaders), String.class);
        assertThat(signInResp.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(signInResp.getBody()).isNotBlank();

        PuzzleAttempt merged = attemptRepository.findById(attemptId).orElseThrow();
        User savedUser = userRepository.findByName(uniqueName);
        assertThat(merged.getUserId()).isEqualTo(savedUser.getId());
        assertThat(merged.getAssignedAt()).isEqualTo(originalAssignedAt); // untouched by merge
        assertThat(merged.getAnonymousId()).isNotNull(); // retained as audit trail
    }

    @Test
    void leaderboardRanksLoggedInUsersByPuzzlesSolvedToday() {
        String userA = registerAndLogin();
        String userB = registerAndLogin();

        completePuzzlesAsUser(userA, 1);
        completePuzzlesAsUser(userB, 2);

        ResponseEntity<LeaderboardEntry[]> resp = restTemplate.getForEntity(
                baseUrl() + "/sudoku/leaderboard?period=daily", LeaderboardEntry[].class);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();

        LeaderboardEntry[] entries = resp.getBody();
        assertThat(entries).isNotEmpty();
        // Whoever solved more (userB, 2) must rank strictly above whoever solved fewer (userA, 1).
        int rankA = rankOf(entries, userA);
        int rankB = rankOf(entries, userB);
        assertThat(rankB).isLessThan(rankA);
    }

    @Test
    void invalidLeaderboardPeriodIsRejected() {
        ResponseEntity<String> resp = restTemplate.getForEntity(baseUrl() + "/sudoku/leaderboard?period=fortnightly", String.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void getUsersNeverExposesPasswordHash() {
        String uniqueName = "itest_pwcheck_" + UUID.randomUUID();
        User newUser = new User(uniqueName, "password123");
        HttpHeaders jsonHeaders = new HttpHeaders();
        jsonHeaders.setContentType(MediaType.APPLICATION_JSON);
        restTemplate.postForEntity(baseUrl() + "/users/addUser", new HttpEntity<>(newUser, jsonHeaders), Boolean.class);

        ResponseEntity<String> signInResp = restTemplate.postForEntity(
                baseUrl() + "/users/signIn", new HttpEntity<>(newUser, jsonHeaders), String.class);
        String token = signInResp.getBody();
        assertThat(token).isNotBlank();

        HttpHeaders authHeaders = new HttpHeaders();
        authHeaders.setBearerAuth(token);
        ResponseEntity<String> usersResp = restTemplate.exchange(
                baseUrl() + "/users", HttpMethod.GET, new HttpEntity<>(authHeaders), String.class);
        assertThat(usersResp.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(usersResp.getBody()).doesNotContain("password");
    }

    /** Registers a fresh user, logs in, and returns the username (unique per call). */
    private String registerAndLogin() {
        String uniqueName = "itest_lb_" + UUID.randomUUID();
        User newUser = new User(uniqueName, "password123");
        HttpHeaders jsonHeaders = new HttpHeaders();
        jsonHeaders.setContentType(MediaType.APPLICATION_JSON);
        restTemplate.postForEntity(baseUrl() + "/users/addUser", new HttpEntity<>(newUser, jsonHeaders), Boolean.class);
        return uniqueName;
    }

    /** Logs the given (already-registered) user in and completes `count` puzzles as them. */
    private void completePuzzlesAsUser(String username, int count) {
        HttpHeaders jsonHeaders = new HttpHeaders();
        jsonHeaders.setContentType(MediaType.APPLICATION_JSON);
        User credentials = new User(username, "password123");
        ResponseEntity<String> signInResp = restTemplate.postForEntity(
                baseUrl() + "/users/signIn", new HttpEntity<>(credentials, jsonHeaders), String.class);
        String token = signInResp.getBody();

        HttpHeaders authHeaders = new HttpHeaders();
        authHeaders.setBearerAuth(token);
        authHeaders.setContentType(MediaType.APPLICATION_JSON);

        for (int i = 0; i < count; i++) {
            ResponseEntity<PuzzleResponse> puzzleResp = restTemplate.exchange(
                    baseUrl() + "/sudoku/puzzle", HttpMethod.GET, new HttpEntity<>(authHeaders), PuzzleResponse.class);
            PuzzleResponse puzzle = puzzleResp.getBody();

            PuzzleAttempt attempt = attemptRepository.findById(puzzle.getAttemptId()).orElseThrow();
            String solution = solveFromClues(attempt.getClueGrid());

            SubmitRequest submitRequest = new SubmitRequest();
            submitRequest.setAttemptId(puzzle.getAttemptId());
            submitRequest.setGrid(solution);
            ResponseEntity<SubmitResponse> submitResp = restTemplate.exchange(
                    baseUrl() + "/sudoku/submit", HttpMethod.POST,
                    new HttpEntity<>(submitRequest, authHeaders), SubmitResponse.class);
            assertThat(submitResp.getBody().isCorrect()).isTrue();
        }
    }

    private String solveFromClues(String clueGrid) {
        int[][] solved = generatorService.solve(generatorService.fromStringGrid(clueGrid));
        return generatorService.toStringGrid(solved);
    }

    private int rankOf(LeaderboardEntry[] entries, String displayName) {
        for (LeaderboardEntry entry : entries) {
            if (entry.getDisplayName().equals(displayName)) {
                return entry.getRank();
            }
        }
        throw new AssertionError(displayName + " not found on leaderboard");
    }
}