package com.raul.chat.dtos.auth;

import com.raul.chat.models.user.Role;
import com.raul.chat.models.user.Status;

import java.util.UUID;

public record UserDto(
        UUID userId,
        String firstName,
        String lastName,
        String email,
        Role role,
        String imageUrl,
        Status status
) {
}
