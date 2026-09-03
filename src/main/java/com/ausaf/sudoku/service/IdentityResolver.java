package com.ausaf.sudoku.service;

import com.ausaf.sudoku.entity.User;
import com.ausaf.sudoku.repository.user.UserRepository;
import com.ausaf.sudoku.security.CallerIdentity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Turns a {@link CallerIdentity} (username-or-guest) into a {@link ResolvedIdentity} usable for ownership checks. */
@Service
public class IdentityResolver {

    @Autowired
    private UserRepository userRepository;

    /**
     * Looks up the real account behind an authenticated identity, or passes a guest's
     * anonymous id through as-is.
     *
     * @throws ResponseStatusException 401 if the identity claims to be a logged-in user whose
     *         account no longer exists; 400 if it's a guest with no anonymous session id at all.
     */
    public ResolvedIdentity resolve(CallerIdentity identity) {
        if (identity.isAuthenticated()) {
            User user = userRepository.findByName(identity.getUsername());
            if (user == null) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found");
            }
            return new ResolvedIdentity(user.getId(), null);
        }
        if (identity.getAnonymousId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing guest session");
        }
        return new ResolvedIdentity(null, identity.getAnonymousId());
    }
}