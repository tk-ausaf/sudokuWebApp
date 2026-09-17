package com.ausaf.sudoku.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

/** Request body for {@code PATCH /users/email}. */
@Data
@NoArgsConstructor
public class UpdateEmailRequest {
    private String email;
}