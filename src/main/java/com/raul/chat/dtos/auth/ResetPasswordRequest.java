package com.raul.chat.dtos.auth;

import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        String token,

        @Size(min = 8, message = "Password should be greater than 8 characters")
        String newPassword,

        @Size(min = 8, message = "Password should be greater than 8 characters")
        String passwordRepeated
) {
}
