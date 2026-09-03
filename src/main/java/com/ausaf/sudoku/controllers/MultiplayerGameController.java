package com.ausaf.sudoku.controllers;

import com.ausaf.sudoku.dto.MultiplayerCreateGameRequest;
import com.ausaf.sudoku.dto.MultiplayerGameCreatedResponse;
import com.ausaf.sudoku.dto.MultiplayerGameStateResponse;
import com.ausaf.sudoku.security.CallerIdentity;
import com.ausaf.sudoku.security.GuestCookieService;
import com.ausaf.sudoku.service.MultiplayerGameService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Create/join/read endpoints for the 2-player multiplayer mode. Guest-allowed, same identity
 * model as {@link com.ausaf.sudoku.controllers.SudokuController} - entirely separate feature,
 * shares no data with single-player attempts or the leaderboard.
 */
@RestController
@RequestMapping("multiplayer")
public class MultiplayerGameController {

    @Autowired
    private MultiplayerGameService multiplayerGameService;

    /** Creates a new game with the caller as player 1 and a fresh unique-solution puzzle. Guest-allowed. */
    @PostMapping("games")
    @ResponseStatus(HttpStatus.CREATED)
    public MultiplayerGameCreatedResponse createGame(@RequestBody MultiplayerCreateGameRequest createRequest,
                                                       HttpServletRequest request) {
        return multiplayerGameService.createGame(currentIdentity(request), createRequest.getMoveTimeLimitSeconds());
    }

    /** The caller joins an existing waiting game as player 2, starting play. Guest-allowed. */
    @PostMapping("games/{gameId}/join")
    public MultiplayerGameStateResponse joinGame(@PathVariable String gameId, HttpServletRequest request) {
        return multiplayerGameService.joinGame(currentIdentity(request), gameId);
    }

    /** Current state of one game, for the initial page load or a manual refresh. Guest-allowed. */
    @GetMapping("games/{gameId}")
    public MultiplayerGameStateResponse getGame(@PathVariable String gameId, HttpServletRequest request) {
        return multiplayerGameService.getState(currentIdentity(request), gameId);
    }

    /** Resolves the caller as a real user (from SecurityContext) or, failing that, a guest (from the request attribute). */
    private CallerIdentity currentIdentity(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            return CallerIdentity.ofUser(auth.getName());
        }
        Object anonymousId = request.getAttribute(GuestCookieService.REQUEST_ATTR);
        return CallerIdentity.ofGuest(anonymousId != null ? anonymousId.toString() : null);
    }
}