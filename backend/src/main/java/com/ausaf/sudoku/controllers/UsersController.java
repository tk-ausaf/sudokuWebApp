package com.ausaf.sudoku.controllers;

import com.ausaf.sudoku.dto.ForgotPasswordRequest;
import com.ausaf.sudoku.dto.MessageResponse;
import com.ausaf.sudoku.dto.ResetPasswordRequest;
import com.ausaf.sudoku.dto.UpdateEmailRequest;
import com.ausaf.sudoku.dto.UserProfileResponse;
import com.ausaf.sudoku.dto.UserSummaryResponse;
import com.ausaf.sudoku.entity.User;
import com.ausaf.sudoku.security.GuestSessionFilter;
import com.ausaf.sudoku.service.AttemptOwnershipService;
import com.ausaf.sudoku.service.PasswordResetService;
import com.ausaf.sudoku.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Account endpoints: list users, register, sign in (which also merges any guest progress), and
 * forgot/reset password.
 */
@RestController
@RequestMapping("users")
public class UsersController {

    @Autowired
    private UserService userService;

    @Autowired
    private AttemptOwnershipService attemptOwnershipService;

    @Autowired
    private PasswordResetService passwordResetService;

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
     * Authenticates and, on success, merges any guest-attributed attempts into the account before
     * returning a JWT (or null on bad credentials).
     */
    @PostMapping("signIn")
    public String signIn(@RequestBody User user, HttpServletRequest request) {
        if (!userService.authenticateUser(user.getName(), user.getPassword())) {
            return null;
        }

        String token = userService.generateToken(user.getName());

        // The client-supplied guest id (see GuestSessionFilter) this exact request carried, if
        // any - merging it in is a deliberate best-effort convenience, not a security boundary,
        // since the id itself is unsigned/spoofable by design.
        Object anonymousId = request.getAttribute(GuestSessionFilter.REQUEST_ATTR);
        if (anonymousId != null) {
            User authenticated = userService.findByName(user.getName());
            attemptOwnershipService.reassignGuestAttempts(anonymousId.toString(), authenticated.getId());
        }

        return token;
    }

    /** The signed-in caller's own name and recovery email (null if they haven't added one yet). */
    @GetMapping("me")
    public UserProfileResponse getMe(Authentication authentication) {
        User user = userService.findByName(authentication.getName());
        return new UserProfileResponse(user.getName(), user.getEmail());
    }

    /** Sets or updates the signed-in caller's recovery email - used to receive password-reset links. */
    @PatchMapping("email")
    public UserProfileResponse updateEmail(Authentication authentication, @RequestBody UpdateEmailRequest request) {
        User user = userService.updateEmail(authentication.getName(), request.getEmail());
        return new UserProfileResponse(user.getName(), user.getEmail());
    }

    /**
     * Always returns the same message, whether or not {@code email} belongs to an account -
     * this can't be used to discover which emails are registered.
     */
    @PostMapping("forgot-password")
    public MessageResponse forgotPassword(@RequestBody ForgotPasswordRequest request) {
        passwordResetService.requestReset(request.getEmail());
        return new MessageResponse("If an account with that email exists, a password reset link has been sent.");
    }

    /** Consumes a password-reset token (from the emailed link) and sets a new password. */
    @PostMapping("reset-password")
    public MessageResponse resetPassword(@RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request.getToken(), request.getNewPassword());
        return new MessageResponse("Your password has been reset - you can log in now.");
    }
}