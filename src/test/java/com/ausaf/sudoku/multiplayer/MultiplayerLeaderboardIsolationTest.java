package com.ausaf.sudoku.multiplayer;

import com.ausaf.sudoku.dto.LeaderboardEntry;
import com.ausaf.sudoku.dto.MultiplayerCreateGameRequest;
import com.ausaf.sudoku.dto.MultiplayerGameCreatedResponse;
import com.ausaf.sudoku.dto.MultiplayerGameStateResponse;
import com.ausaf.sudoku.entity.MultiplayerGame;
import com.ausaf.sudoku.entity.MultiplayerGameStatus;
import com.ausaf.sudoku.entity.User;
import com.ausaf.sudoku.repository.attempt.PuzzleAttemptRepository;
import com.ausaf.sudoku.repository.multiplayer.MultiplayerGameRepository;
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
 * document - the two features share no data by construction ({@code MultiplayerGame} lives in
 * its own {@code multiplayer_games} collection), verified here end to end rather than by
 * inspection alone.
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
    private MultiplayerGameRepository multiplayerGameRepository;

    @Autowired
    private SudokuGeneratorService generatorService;

    @Autowired
    private MultiplayerGameEngine gameEngine;

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    /** Playing a multiplayer game to completion between two logged-in users leaves puzzle_attempts and the leaderboard untouched. */
    @Test
    void completedMultiplayerGameNeverTouchesSinglePlayerData() throws InterruptedException {
        String userA = registerAndLogin();
        String userB = registerAndLogin();
        long attemptsBefore = attemptRepository.count();

        MultiplayerCreateGameRequest createRequest = new MultiplayerCreateGameRequest();
        createRequest.setMoveTimeLimitSeconds(60);
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

        // A deliberately wrong digit ends the game immediately, so this game reaches COMPLETED
        // without needing to fill the whole board.
        gameEngine.applyMove(created.getGameId(), CallerIdentity.ofUser(userA), row, col, wrongValue);

        MultiplayerGame persisted = awaitGameCompleted(created.getGameId());
        assertThat(persisted.getStatus()).isEqualTo(MultiplayerGameStatus.COMPLETED);

        ResponseEntity<LeaderboardEntry[]> leaderboardResp = restTemplate.getForEntity(
                baseUrl() + "/sudoku/leaderboard?period=daily", LeaderboardEntry[].class);
        assertThat(leaderboardResp.getStatusCode().is2xxSuccessful()).isTrue();
        for (LeaderboardEntry entry : leaderboardResp.getBody()) {
            assertThat(entry.getDisplayName()).isNotIn(userA, userB);
        }

        assertThat(attemptRepository.count()).isEqualTo(attemptsBefore);
    }

    private MultiplayerGame awaitGameCompleted(String gameId) throws InterruptedException {
        long deadlineMs = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadlineMs) {
            MultiplayerGame doc = multiplayerGameRepository.findById(gameId).orElse(null);
            if (doc != null && doc.getStatus() == MultiplayerGameStatus.COMPLETED) {
                return doc;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Game was never persisted as COMPLETED within 5s");
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