package com.ausaf.sudoku.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

/** Request body for {@code PATCH /sudoku/attempts/{id}/grid}: the latest in-progress cell values. */
@Data
@NoArgsConstructor
public class AutosaveRequest {
    /** 81 chars, row-major, '0' marks a blank cell. */
    private String grid;
}