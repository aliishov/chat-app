package com.raul.chat.dtos.chat;

import com.raul.chat.dtos.auth.UserDto;

public record NotificationDto(
        UserDto userDto,
        String messageContent
) {
}
