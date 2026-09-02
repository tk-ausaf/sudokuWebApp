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

@RestController
@RequestMapping("users")
public class UsersController {

    @Autowired
    private UserService userService;

    @Autowired
    private GuestCookieService guestCookieService;

    @Autowired
    private AttemptOwnershipService attemptOwnershipService;

    @GetMapping
    public List<UserSummaryResponse> getAllUsers() {
        return userService.getAllUsers().stream()
                .map(u -> new UserSummaryResponse(u.getId(), u.getName()))
                .toList();
    }

    @PostMapping("addUser")
    public Boolean addUser(@RequestBody User user) {
        return userService.addUser(user);
    }

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