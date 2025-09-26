package com.raul.chat.dtos.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequestDto(

        @NotBlank(message = "Email should not be empty")
        @Email(message = "Email should be in the right format")
        String email,

        @Size(min = 8, message = "Password should be greater than 8 characters")
        String password
) {
}
