package com.ausaf.sudoku.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

/** Request body for {@code POST /users/forgot-password}. */
@Data
@NoArgsConstructor
public class ForgotPasswordRequest {
    private String email;
}