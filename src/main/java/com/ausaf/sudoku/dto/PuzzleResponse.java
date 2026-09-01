package com.ausaf.sudoku.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PuzzleResponse {
    private String attemptId;
    private String puzzleId;
    /** 81 chars, row-major, '0' marks a blank cell to be filled in. */
    private String grid;
}