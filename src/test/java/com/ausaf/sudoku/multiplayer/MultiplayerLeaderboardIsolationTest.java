package com.ausaf.sudoku.multiplayer;

import com.ausaf.sudoku.dto.LeaderboardEntry;
import com.ausaf.sudoku.dto.MultiplayerCreateGameRequest;
import com.ausaf.sudoku.dto.MultiplayerGameCreatedResponse;
import com.ausaf.sudoku.dto.MultiplayerGameStateResponse;
import com.ausaf.sudoku.entity.User;
import com.ausaf.sudoku.repository.attempt.PuzzleAttemptRepository;
import com.ausaf.sudoku.security.CallerIdentity;
import com.ausaf.sudoku.service.MultiplayerGameEngine;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Confirms a completed multiplayer game between two real accounts never appears on, or
 * contributes to, the single-player leaderboard, and creates no {@code puzzle_attempts}
 * document - the two features share no data by construction (a multiplayer game is never
 * persisted at all), verified here end to end rather than by inspection alone.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MultiplayerLeaderboardIsolationTest {

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private PuzzleAttemptRepository attemptRepository;

    @Autowired
    private SudokuGeneratorService generatorService;

    @Autowired
    private MultiplayerGameEngine gameEngine;

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    /** Playing a multiplayer game to completion between two logged-in users leaves puzzle_attempts and the leaderboard untouched. */
    @Test
    void completedMultiplayerGameNeverTouchesSinglePlayerData() {
        String userA = registerAndLogin();
        String userB = registerAndLogin();
        long attemptsBefore = attemptRepository.count();

        MultiplayerCreateGameRequest createRequest = new MultiplayerCreateGameRequest();
        createRequest.setMoveTimeLimitSeconds(60);
        createRequest.setMaxWrongAttempts(1); // one wrong digit should immediately end the game below
        ResponseEntity<MultiplayerGameCreatedResponse> createResp = restTemplate.exchange(
                baseUrl() + "/multiplayer/games", HttpMethod.POST,
                new HttpEntity<>(createRequest, authHeaders(userA)), MultiplayerGameCreatedResponse.class);
        MultiplayerGameCreatedResponse created = createResp.getBody();

        restTemplate.exchange(baseUrl() + "/multiplayer/games/" + created.getGameId() + "/join",
                HttpMethod.POST, new HttpEntity<>(authHeaders(userB)), MultiplayerGameStateResponse.class);

        int[][] solved = generatorService.solve(generatorService.fromStringGrid(created.getClueGrid()));
        int cellIndex = created.getClueGrid().indexOf('0');
        int row = cellIndex / 9;
        int col = cellIndex % 9;
        int correctValue = solved[row][col];
        int wrongValue = correctValue == 9 ? 1 : correctValue + 1;

        // A deliberately wrong digit ends the game immediately - applyMove is synchronous, so by
        // the time it returns the game has already ended and been dropped from memory for good.
        gameEngine.applyMove(created.getGameId(), CallerIdentity.ofUser(userA), row, col, wrongValue);

        ResponseEntity<String> stateAfterEnd = restTemplate.exchange(
                baseUrl() + "/multiplayer/games/" + created.getGameId(), HttpMethod.GET,
                new HttpEntity<>(authHeaders(userA)), String.class);
        assertThat(stateAfterEnd.getStatusCode().value())
                .as("an ended multiplayer game must not be resumable/queryable")
                .isEqualTo(404);

        ResponseEntity<LeaderboardEntry[]> leaderboardResp = restTemplate.getForEntity(
                baseUrl() + "/sudoku/leaderboard?period=daily", LeaderboardEntry[].class);
        assertThat(leaderboardResp.getStatusCode().is2xxSuccessful()).isTrue();
        for (LeaderboardEntry entry : leaderboardResp.getBody()) {
            assertThat(entry.getDisplayName()).isNotIn(userA, userB);
        }

        assertThat(attemptRepository.count()).isEqualTo(attemptsBefore);
    }

    private String registerAndLogin() {
        String uniqueName = "itest_mp_" + UUID.randomUUID();
        User newUser = new User(uniqueName, "password123");
        HttpHeaders jsonHeaders = new HttpHeaders();
        jsonHeaders.setContentType(MediaType.APPLICATION_JSON);
        restTemplate.postForEntity(baseUrl() + "/users/addUser", new HttpEntity<>(newUser, jsonHeaders), Boolean.class);
        return uniqueName;
    }

    private HttpHeaders authHeaders(String username) {
        HttpHeaders jsonHeaders = new HttpHeaders();
        jsonHeaders.setContentType(MediaType.APPLICATION_JSON);
        User credentials = new User(username, "password123");
        ResponseEntity<String> signInResp = restTemplate.postForEntity(
                baseUrl() + "/users/signIn", new HttpEntity<>(credentials, jsonHeaders), String.class);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(signInResp.getBody());
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
