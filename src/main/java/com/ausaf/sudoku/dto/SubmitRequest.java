package com.ausaf.sudoku.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class SubmitRequest {
    private String attemptId;
    /** 81 chars, row-major, digits 1-9 only (no blanks allowed at submit time). */
    private String grid;
}