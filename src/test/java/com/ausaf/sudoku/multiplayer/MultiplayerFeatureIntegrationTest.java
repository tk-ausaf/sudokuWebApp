package com.ausaf.sudoku.multiplayer;

import com.ausaf.sudoku.dto.MultiplayerCreateGameRequest;
import com.ausaf.sudoku.dto.MultiplayerGameCreatedResponse;
import com.ausaf.sudoku.dto.MultiplayerGameStateResponse;
import com.ausaf.sudoku.entity.MultiplayerGameStatus;
import com.ausaf.sudoku.security.CallerIdentity;
import com.ausaf.sudoku.security.JwtUtil;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end verification of the multiplayer create/join/move REST flow, mirroring
 * {@code SudokuFeatureIntegrationTest}'s style. A multiplayer game is never persisted - it exists
 * only in memory for as long as it's active - so every assertion here reads back through the
 * {@code GET /multiplayer/games/{id}} state endpoint rather than a database.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MultiplayerFeatureIntegrationTest {

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private SudokuGeneratorService generatorService;

    @Autowired
    private MultiplayerGameEngine gameEngine;

    @Autowired
    private JwtUtil jwtUtil;

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    /** Two distinct guests: creator becomes player 1, a second guest joining starts the game. */
    @Test
    void secondGuestJoiningStartsTheGame() {
        MultiplayerGameCreatedResponse created = createGame(headersWithCookie(newGuestCookie()), 30);
        assertThat(created.getStatus()).isEqualTo(MultiplayerGameStatus.WAITING_FOR_OPPONENT);

        ResponseEntity<MultiplayerGameStateResponse> joinResp = restTemplate.exchange(
                baseUrl() + "/multiplayer/games/" + created.getGameId() + "/join", HttpMethod.POST,
                new HttpEntity<>(headersWithCookie(newGuestCookie())), MultiplayerGameStateResponse.class);

        assertThat(joinResp.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(joinResp.getBody().getStatus()).isEqualTo(MultiplayerGameStatus.IN_PROGRESS);
    }

    /** A third guest (not the creator) trying to join a game that already has two players is rejected. */
    @Test
    void thirdPartyCannotJoinAFullGame() {
        MultiplayerGameCreatedResponse created = createGame(headersWithCookie(newGuestCookie()), 30);
        restTemplate.exchange(baseUrl() + "/multiplayer/games/" + created.getGameId() + "/join",
                HttpMethod.POST, new HttpEntity<>(headersWithCookie(newGuestCookie())), MultiplayerGameStateResponse.class);

        ResponseEntity<String> thirdJoin = restTemplate.exchange(
                baseUrl() + "/multiplayer/games/" + created.getGameId() + "/join", HttpMethod.POST,
                new HttpEntity<>(headersWithCookie(newGuestCookie())), String.class);
        assertThat(thirdJoin.getStatusCode().value()).isEqualTo(409);
    }

    /** The game's own creator cannot join their own game as the second player. */
    @Test
    void creatorCannotJoinTheirOwnGame() {
        String creatorCookie = newGuestCookie();
        MultiplayerGameCreatedResponse created = createGame(headersWithCookie(creatorCookie), 30);

        ResponseEntity<String> selfJoin = restTemplate.exchange(
                baseUrl() + "/multiplayer/games/" + created.getGameId() + "/join", HttpMethod.POST,
                new HttpEntity<>(headersWithCookie(creatorCookie)), String.class);
        assertThat(selfJoin.getStatusCode().value()).isEqualTo(400);
    }

    /** A move applied through the engine is reflected immediately in the in-memory game state. */
    @Test
    void acceptedMoveIsReflectedInGameState() {
        String creatorCookie = newGuestCookie();
        MultiplayerGameCreatedResponse created = createGame(headersWithCookie(creatorCookie), 60);

        restTemplate.exchange(baseUrl() + "/multiplayer/games/" + created.getGameId() + "/join",
                HttpMethod.POST, new HttpEntity<>(headersWithCookie(newGuestCookie())), MultiplayerGameStateResponse.class);

        int[][] solved = generatorService.solve(generatorService.fromStringGrid(created.getClueGrid()));
        int cellIndex = created.getClueGrid().indexOf('0');
        int row = cellIndex / 9;
        int col = cellIndex % 9;
        int value = solved[row][col];

        gameEngine.applyMove(created.getGameId(), CallerIdentity.ofGuest(anonymousIdFromCookie(creatorCookie)), row, col, value);

        ResponseEntity<MultiplayerGameStateResponse> stateResp = restTemplate.exchange(
                baseUrl() + "/multiplayer/games/" + created.getGameId(), HttpMethod.GET,
                new HttpEntity<>(headersWithCookie(creatorCookie)), MultiplayerGameStateResponse.class);

        assertThat(stateResp.getBody().getCurrentGrid().charAt(cellIndex)).isEqualTo(Character.forDigit(value, 10));
    }

    /** Once a game ends, it's gone - not resumable, unlike a single-player attempt. */
    @Test
    void endedGameIsNoLongerQueryable() {
        String creatorCookie = newGuestCookie();
        MultiplayerGameCreatedResponse created = createGame(headersWithCookie(creatorCookie), 60);
        restTemplate.exchange(baseUrl() + "/multiplayer/games/" + created.getGameId() + "/join",
                HttpMethod.POST, new HttpEntity<>(headersWithCookie(newGuestCookie())), MultiplayerGameStateResponse.class);

        int[][] solved = generatorService.solve(generatorService.fromStringGrid(created.getClueGrid()));
        int cellIndex = created.getClueGrid().indexOf('0');
        int correctValue = solved[cellIndex / 9][cellIndex % 9];
        int wrongValue = correctValue == 9 ? 1 : correctValue + 1;

        // A wrong digit with the default 1-wrong-attempt allowance ends the game immediately.
        gameEngine.applyMove(created.getGameId(), CallerIdentity.ofGuest(anonymousIdFromCookie(creatorCookie)),
                cellIndex / 9, cellIndex % 9, wrongValue);

        ResponseEntity<String> stateResp = restTemplate.exchange(
                baseUrl() + "/multiplayer/games/" + created.getGameId(), HttpMethod.GET,
                new HttpEntity<>(headersWithCookie(creatorCookie)), String.class);
        assertThat(stateResp.getStatusCode().value()).isEqualTo(404);
    }

    private MultiplayerGameCreatedResponse createGame(HttpHeaders headers, int moveTimeLimitSeconds) {
        MultiplayerCreateGameRequest createRequest = new MultiplayerCreateGameRequest();
        createRequest.setMoveTimeLimitSeconds(moveTimeLimitSeconds);
        createRequest.setMaxWrongAttempts(1);
        ResponseEntity<MultiplayerGameCreatedResponse> resp = restTemplate.exchange(
                baseUrl() + "/multiplayer/games", HttpMethod.POST,
                new HttpEntity<>(createRequest, headers), MultiplayerGameCreatedResponse.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(201);
        return resp.getBody();
    }

    /** Establishes a brand new guest identity (via the existing single-player endpoint) and returns its raw {@code Set-Cookie} value. */
    private String newGuestCookie() {
        ResponseEntity<Void> resp = restTemplate.getForEntity(baseUrl() + "/sudoku/puzzle", Void.class);
        return resp.getHeaders().getFirst(HttpHeaders.SET_COOKIE).split(";")[0];
    }

    private HttpHeaders headersWithCookie(String cookie) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, cookie);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    /** Decodes the guest session id (the JWT's subject) out of a raw {@code sudoku_guest=<token>} cookie value. */
    private String anonymousIdFromCookie(String cookie) {
        String token = cookie.substring(cookie.indexOf('=') + 1);
        return jwtUtil.getSubject(token);
    }
}
