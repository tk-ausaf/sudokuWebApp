package com.ausaf.sudoku.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

/** Request body for {@code POST /sudoku/submit}: the attempt being solved and the proposed grid. */
@Data
@NoArgsConstructor
public class SubmitRequest {
    private String attemptId;
    /** 81 chars, row-major, digits 1-9 only (no blanks allowed at submit time). */
    private String grid;
}