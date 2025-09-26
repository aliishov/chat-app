package com.raul.chat.dtos.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RefreshTokenRequest(

        @NotNull
        @NotBlank(message = "Refresh token should not be empty")
        String refreshToken
) {
}
