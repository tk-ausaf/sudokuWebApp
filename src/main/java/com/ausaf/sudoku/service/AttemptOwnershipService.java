package com.ausaf.sudoku.service;

import com.ausaf.sudoku.entity.PuzzleAttempt;
import com.ausaf.sudoku.repository.attempt.PuzzleAttemptRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Re-owns a guest's attempts to their account on login, preserving the original
 * assignedAt (true start time) so the leaderboard timer can't be gamed by solving
 * anonymously and logging in right before submitting.
 */
@Service
public class AttemptOwnershipService {

    @Autowired
    private PuzzleAttemptRepository attemptRepository;

    /**
     * Re-owns every attempt still tied to {@code anonymousId} to {@code userId}, leaving
     * assignedAt and anonymousId itself untouched. No-op if either argument is null.
     */
    public void reassignGuestAttempts(String anonymousId, String userId) {
        if (anonymousId == null || userId == null) {
            return;
        }
        List<PuzzleAttempt> attempts = attemptRepository.findByAnonymousId(anonymousId);
        for (PuzzleAttempt attempt : attempts) {
            if (attempt.getUserId() == null) {
                attempt.setUserId(userId);
                attemptRepository.save(attempt);
            }
        }
    }
}