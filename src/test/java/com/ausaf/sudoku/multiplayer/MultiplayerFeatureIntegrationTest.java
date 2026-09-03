package com.ausaf.sudoku.multiplayer;

import com.ausaf.sudoku.dto.MultiplayerCreateGameRequest;
import com.ausaf.sudoku.dto.MultiplayerGameCreatedResponse;
import com.ausaf.sudoku.dto.MultiplayerGameStateResponse;
import com.ausaf.sudoku.entity.MultiplayerGame;
import com.ausaf.sudoku.entity.MultiplayerGameStatus;
import com.ausaf.sudoku.repository.multiplayer.MultiplayerGameRepository;
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
 * End-to-end verification of the multiplayer create/join REST flow and its async move
 * persistence, exercised against a real (locally running) MongoDB instance, mirroring
 * {@code SudokuFeatureIntegrationTest}'s style. Drives moves directly through
 * {@link MultiplayerGameEngine} rather than a live STOMP client, which is enough to prove the
 * async-persistence path end to end without standing up a WebSocket test client.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MultiplayerFeatureIntegrationTest {

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private MultiplayerGameRepository gameRepository;

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

    /**
     * A move applied through the engine is written off the calling thread - immediately after
     * the call returns the write may not have landed yet, but polling the repository finds it
     * within a few seconds, proving persistence happens asynchronously rather than not at all.
     */
    @Test
    void acceptedMoveIsEventuallyPersisted() throws InterruptedException {
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

        MultiplayerGame persisted = awaitPersistedCellFilled(created.getGameId(), cellIndex);

        assertThat(persisted.getCurrentGrid().charAt(cellIndex)).isEqualTo(Character.forDigit(value, 10));
    }

    private MultiplayerGameCreatedResponse createGame(HttpHeaders headers, int moveTimeLimitSeconds) {
        MultiplayerCreateGameRequest createRequest = new MultiplayerCreateGameRequest();
        createRequest.setMoveTimeLimitSeconds(moveTimeLimitSeconds);
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

    private MultiplayerGame awaitPersistedCellFilled(String gameId, int cellIndex) throws InterruptedException {
        long deadlineMs = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadlineMs) {
            MultiplayerGame persisted = gameRepository.findById(gameId).orElse(null);
            if (persisted != null && persisted.getCurrentGrid() != null && persisted.getCurrentGrid().charAt(cellIndex) != '0') {
                return persisted;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Move was never persisted within 5s");
    }
}