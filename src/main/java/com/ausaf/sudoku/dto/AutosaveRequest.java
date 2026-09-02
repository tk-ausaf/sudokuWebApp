package com.ausaf.sudoku.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class AutosaveRequest {
    /** 81 chars, row-major, '0' marks a blank cell. */
    private String grid;
}