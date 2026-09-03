package com.ausaf.sudoku.service;

/**
 * Thrown when a submitted multiplayer move is malformed or out-of-turn client noise (not a
 * participant, not their turn, the game isn't in progress, or an invalid/already-filled cell) -
 * reported only to the sender's private error queue and never ends the game, unlike a wrong
 * digit or a missed deadline.
 */
public class MultiplayerMoveRejectedException extends RuntimeException {
    public MultiplayerMoveRejectedException(String message) {
        super(message);
    }
}
