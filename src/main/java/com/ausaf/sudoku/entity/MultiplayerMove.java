package com.ausaf.sudoku.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** One audit-trail entry in a {@link MultiplayerGame}'s embedded move history. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MultiplayerMove {
    private PlayerSlot player;
    private int row;
    private int col;
    private int value;
    private boolean correct;
    private LocalDateTime submittedAt;
}
