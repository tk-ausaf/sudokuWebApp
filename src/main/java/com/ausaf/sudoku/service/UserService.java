package com.ausaf.sudoku.service;

import com.ausaf.sudoku.entity.User;
import com.ausaf.sudoku.repository.user.UserRepository;
import com.ausaf.sudoku.security.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/** Account registration, authentication, and JWT issuance for {@link User}s. */
@Slf4j
@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtUtil jwtUtil;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /**
     * Registers a new account, BCrypt-hashing the supplied plaintext password.
     *
     * @return false if the username is already taken (no account is created)
     */
    public boolean addUser(User user) {
        if (userRepository.findByName(user.getName()) != null) {
            log.warn("Registration rejected: username '{}' already taken", user.getName());
            return false;
        }
        String encryptedPassword = passwordEncoder.encode(user.getPassword());
        user.setPassword(encryptedPassword);
        user.setId(null);
        userRepository.save(user);
        log.info("Registered new user '{}'", user.getName());
        return true;
    }

    /** @return every registered account, password hashes included - callers must not expose this raw. */
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    /** @return the account with this username, or null if none exists. */
    public User findByName(String name) {
        return userRepository.findByName(name);
    }

    /** @return true if an account with this username exists and the password matches its BCrypt hash. */
    public boolean authenticateUser(String name, String password) {
        User user = userRepository.findByName(name);
        if (user == null) {
            log.warn("Login failed: no account named '{}'", name);
            return false;
        }
        boolean matches = passwordEncoder.matches(password, user.getPassword());
        if (matches) {
            log.info("User '{}' logged in", name);
        } else {
            log.warn("Login failed: wrong password for '{}'", name);
        }
        return matches;
    }

    /** Issues a real-user JWT for an already-authenticated username. */
    public String generateToken(String name) {
        return jwtUtil.generateToken(name);
    }

    /**
     * Finds or creates the account for a Google-authenticated identity. Matches, in order: (1)
     * an account already linked to this Google subject ("sub") id - an ordinary repeat sign-in;
     * (2) an account that has this same email on file (e.g. added earlier via
     * {@link #updateEmail} on a username/password account) - this is the "same email, get the
     * same account" link the caller asked for, so it links this Google identity onto that
     * account rather than creating a second one; (3) otherwise, a brand new Google-only account.
     *
     * @throws ResponseStatusException 409 if {@code email} coincidentally matches an existing
     *         account's <em>username</em> (not its verified email field) - since that username
     *         was never verified as belonging to this email, it's rejected as a naming
     *         collision rather than silently taking over that account.
     */
    public User resolveGoogleUser(String googleId, String email) {
        User existing = userRepository.findByGoogleId(googleId);
        if (existing != null) {
            log.info("Google user '{}' logged in", existing.getName());
            return existing;
        }

        User byEmail = userRepository.findByEmail(email);
        if (byEmail != null) {
            byEmail.setGoogleId(googleId);
            userRepository.save(byEmail);
            log.info("Linked Google identity to existing account '{}' by matching email", byEmail.getName());
            return byEmail;
        }

        if (userRepository.findByName(email) != null) {
            log.warn("Google login rejected: an account named '{}' already exists without a matching email or Google identity", email);
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "An account with this email already exists - log in with your password instead");
        }

        User user = new User();
        user.setName(email);
        user.setEmail(email);
        user.setGoogleId(googleId);
        userRepository.save(user);
        log.info("Registered new Google user '{}'", email);
        return user;
    }

    /**
     * Sets or updates the recovery email on an existing account, e.g. from the "add a recovery
     * email" prompt shown to users who don't have one yet. Used later for password-reset links,
     * and for matching a future Google sign-in to this same account.
     *
     * @throws ResponseStatusException 400 if {@code email} is blank or missing an "@"; 409 if
     *         it's already on file for a different account (email must stay unique so a Google
     *         sign-in can match exactly one account by it).
     */
    public User updateEmail(String name, String email) {
        if (email == null || email.isBlank() || !email.contains("@")) {
            log.warn("Rejected email update for '{}': not a valid email address", name);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Not a valid email address");
        }

        User user = userRepository.findByName(name);
        User existingOwner = userRepository.findByEmail(email);
        if (existingOwner != null && !existingOwner.getId().equals(user.getId())) {
            log.warn("Rejected email update for '{}': '{}' already belongs to another account", name, email);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That email is already in use");
        }

        user.setEmail(email);
        userRepository.save(user);
        log.info("Recovery email added for user '{}'", name);
        return user;
    }

}
