package com.ausaf.sudoku.service;

import com.ausaf.sudoku.entity.User;
import com.ausaf.sudoku.repository.user.UserRepository;
import com.ausaf.sudoku.security.CallerIdentity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class IdentityResolver {

    @Autowired
    private UserRepository userRepository;

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