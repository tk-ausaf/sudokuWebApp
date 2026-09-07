package com.ausaf.sudoku.service;

import com.ausaf.sudoku.entity.User;
import com.ausaf.sudoku.repository.user.UserRepository;
import com.ausaf.sudoku.security.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

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

}
