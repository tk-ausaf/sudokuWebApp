package com.ausaf.sudoku.security;

import com.ausaf.sudoku.entity.User;
import com.ausaf.sudoku.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Completes a successful Google sign-in: resolves (or provisions) the app's own {@link User}
 * account for the Google identity, mints this app's usual JWT for it, then hands the token back
 * to the SPA via a redirect query param (the frontend keeps its JWT in {@code localStorage}, not
 * a cookie, so a query-param hand-off is how a server-side OAuth2 redirect gets the token to it).
 * Redirects to {@code app.base-url} (the frontend's own origin, not this backend's) rather than a
 * relative path, since the two are separate deployed services.
 *
 * <p>Unlike {@code UsersController.signIn}, this does <b>not</b> merge guest progress into the
 * new account. The guest id now travels as an {@code X-Guest-Id} header on plain fetch/XHR calls
 * (see {@link GuestSessionFilter}), which a top-level OAuth2 redirect flow can't carry - and this
 * app is stateless ({@code SessionCreationPolicy.STATELESS}), so there's nowhere server-side to
 * stash it between the initial redirect to Google and this callback. A guest who signs up via
 * Google specifically loses their in-progress guest attempts; one signing up with a password
 * (which is a real fetch call, header intact) does not.
 */
@Slf4j
@Component
public class GoogleOAuth2SuccessHandler implements AuthenticationSuccessHandler {

    @Autowired
    private UserService userService;

    @Value("${app.base-url}")
    private String appBaseUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException {
        OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();
        String googleId = oauth2User.getName();
        String email = oauth2User.getAttribute("email");

        User user;
        try {
            user = userService.resolveGoogleUser(googleId, email);
        } catch (ResponseStatusException e) {
            response.sendRedirect(appBaseUrl + "/login?error=google_account_conflict");
            return;
        }

        String token = userService.generateToken(user.getName());

        log.info("Google login successful for user:{}", user.getId());
        String redirectUrl = appBaseUrl + "/?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8)
                + "&username=" + URLEncoder.encode(user.getName(), StandardCharsets.UTF_8);
        response.sendRedirect(redirectUrl);
    }
}