package com.ausaf.sudoku.service;

import com.ausaf.sudoku.entity.PuzzleAttempt;

/** Owner of a PuzzleAttempt: exactly one of userId/anonymousId is set. */
public final class ResolvedIdentity {

    private final String userId;
    private final String anonymousId;

    ResolvedIdentity(String userId, String anonymousId) {
        this.userId = userId;
        this.anonymousId = anonymousId;
    }

    public String getUserId() {
        return userId;
    }

    public String getAnonymousId() {
        return anonymousId;
    }

    public boolean isUser() {
        return userId != null;
    }

    public boolean owns(PuzzleAttempt attempt) {
        if (userId != null) {
            return userId.equals(attempt.getUserId());
        }
        return anonymousId != null && anonymousId.equals(attempt.getAnonymousId());
    }

    public void applyAsOwner(PuzzleAttempt attempt) {
        attempt.setUserId(userId);
        attempt.setAnonymousId(anonymousId);
    }
}