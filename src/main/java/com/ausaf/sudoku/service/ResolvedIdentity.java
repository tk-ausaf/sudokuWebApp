package com.ausaf.sudoku.service;

import com.ausaf.sudoku.entity.PuzzleAttempt;

/** Owner of a PuzzleAttempt: exactly one of userId/anonymousId is set. */
public final class ResolvedIdentity {

    private final String userId;
    private final String anonymousId;

    /** Exactly one argument should be non-null - the resolved owner's userId, or their anonymousId. */
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

    /** Whether this identity is the current owner of {@code attempt} (by userId or anonymousId, whichever applies). */
    public boolean owns(PuzzleAttempt attempt) {
        if (userId != null) {
            return userId.equals(attempt.getUserId());
        }
        return anonymousId != null && anonymousId.equals(attempt.getAnonymousId());
    }

    /** Stamps this identity's userId/anonymousId onto a newly created attempt. */
    public void applyAsOwner(PuzzleAttempt attempt) {
        attempt.setUserId(userId);
        attempt.setAnonymousId(anonymousId);
    }
}