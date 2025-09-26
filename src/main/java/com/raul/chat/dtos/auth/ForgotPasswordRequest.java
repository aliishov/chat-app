package com.raul.chat.dtos.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ForgotPasswordRequest(
        @NotBlank(message = "Email should not be empty")
        @Email(message = "Email should be in the right format")
        String email
) {
}
