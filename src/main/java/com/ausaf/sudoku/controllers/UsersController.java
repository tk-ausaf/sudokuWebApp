package com.ausaf.sudoku.controllers;

import com.ausaf.sudoku.dto.UserSummaryResponse;
import com.ausaf.sudoku.entity.User;
import com.ausaf.sudoku.security.GuestCookieService;
import com.ausaf.sudoku.service.AttemptOwnershipService;
import com.ausaf.sudoku.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Account endpoints: list users, register, and sign in (which also merges any guest progress). */
@RestController
@RequestMapping("users")
public class UsersController {

    @Autowired
    private UserService userService;

    @Autowired
    private GuestCookieService guestCookieService;

    @Autowired
    private AttemptOwnershipService attemptOwnershipService;

    /** Lists every registered account as a safe id/name projection - never the password hash. */
    @GetMapping
    public List<UserSummaryResponse> getAllUsers() {
        return userService.getAllUsers().stream()
                .map(u -> new UserSummaryResponse(u.getId(), u.getName()))
                .toList();
    }

    /** @return false if the username is already taken, true once the account is created. */
    @PostMapping("addUser")
    public Boolean addUser(@RequestBody User user) {
        return userService.addUser(user);
    }

    /**
     * Authenticates and, on success, merges any guest-cookie attempts into the account before
     * returning a JWT (or null on bad credentials).
     */
    @PostMapping("signIn")
    public String signIn(@RequestBody User user, HttpServletRequest request, HttpServletResponse response) {
        if (!userService.authenticateUser(user.getName(), user.getPassword())) {
            return null;
        }

        String token = userService.generateToken(user.getName());

        // Never trust a client-supplied anonymous id here (spoofable) - only the
        // server-validated guest cookie on this exact request is trusted.
        String anonymousId = guestCookieService.extractGuestId(request);
        if (anonymousId != null) {
            User authenticated = userService.findByName(user.getName());
            attemptOwnershipService.reassignGuestAttempts(anonymousId, authenticated.getId());
            guestCookieService.clearGuestCookie(response);
        }

        return token;
    }
}