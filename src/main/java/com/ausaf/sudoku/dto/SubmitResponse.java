package com.ausaf.sudoku.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Response for {@code POST /sudoku/submit}: whether the submitted grid was correct, and why not if not. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubmitResponse {
    private boolean correct;
    private String message;
}