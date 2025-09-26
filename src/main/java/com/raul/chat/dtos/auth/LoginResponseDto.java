package com.raul.chat.dtos.auth;

public record LoginResponseDto(
        String accessToken,
        String refreshToken
) {
}
