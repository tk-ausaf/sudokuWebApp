package com.ausaf.sudoku.service;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds the in-memory {@link ActiveGame} state for every currently active (waiting or
 * in-progress) multiplayer game, keyed by game id. This is the authoritative store while a game
 * is active; its Mongo document is only a write-behind snapshot (see
 * {@link MultiplayerGamePersistenceService}), so completed/inactive games are looked up there
 * instead once removed from here.
 */
@Component
class ActiveGameRegistry {

    private final Map<String, ActiveGame> games = new ConcurrentHashMap<>();

    /** Registers a newly created game, or replaces any existing entry under the same id. */
    void put(ActiveGame game) {
        games.put(game.id, game);
    }

    /** @return the in-memory state for {@code gameId}, or null if it isn't currently active in this process. */
    ActiveGame get(String gameId) {
        return games.get(gameId);
    }

    /** Drops a finished game's in-memory state; its final outcome remains in Mongo. */
    void remove(String gameId) {
        games.remove(gameId);
    }
}