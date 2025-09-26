package com.raul.chat.dtos.chat;

import jakarta.annotation.Nullable;

import java.util.UUID;

public record NewMessageDto(
        String content,
        UUID senderId,
        @Nullable UUID recipientId,
        @Nullable Long chatRoomId
) {
}
