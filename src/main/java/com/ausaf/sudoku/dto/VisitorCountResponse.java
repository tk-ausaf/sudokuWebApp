package com.ausaf.sudoku.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Response for the visitor-counter endpoints: today's unique-visitor count so far. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VisitorCountResponse {
    private long count;
}