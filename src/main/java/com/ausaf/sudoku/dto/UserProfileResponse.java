package com.ausaf.sudoku.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Safe-to-expose projection of the signed-in caller's own account - never includes the password hash. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileResponse {
    private String name;
    /** Recovery email on file, or null if the account hasn't added one yet. */
    private String email;
}