package com.ausaf.sudoku.security;

/** Who is calling: an authenticated user (by username) or a guest (by anonymous session id). */
public final class CallerIdentity {

    private final String username;
    private final String anonymousId;

    private CallerIdentity(String username, String anonymousId) {
        this.username = username;
        this.anonymousId = anonymousId;
    }

    /** Identity for a request authenticated as a real user. */
    public static CallerIdentity ofUser(String username) {
        return new CallerIdentity(username, null);
    }

    /** Identity for an unauthenticated request carrying only an anonymous guest session id. */
    public static CallerIdentity ofGuest(String anonymousId) {
        return new CallerIdentity(null, anonymousId);
    }

    public boolean isAuthenticated() {
        return username != null;
    }

    public String getUsername() {
        return username;
    }

    public String getAnonymousId() {
        return anonymousId;
    }
}