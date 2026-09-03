package com.ausaf.sudoku.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One seat's identity in a {@link MultiplayerGame} - exactly one of {@code userId}/{@code anonymousId}
 * is set, mirroring how {@link PuzzleAttempt} identifies its owner. No display name is cached here
 * to avoid staleness; clients render "You"/"Opponent" instead of a resolved username.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MultiplayerParticipant {
    private String userId;
    private String anonymousId;
}
