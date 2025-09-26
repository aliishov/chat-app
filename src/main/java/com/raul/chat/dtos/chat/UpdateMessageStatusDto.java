package com.raul.chat.dtos.chat;

import com.raul.chat.models.chat.MessageStatus;

import java.util.UUID;

public record UpdateMessageStatusDto(
        Long messageId,
        UUID recipientId,
        MessageStatus status
) {
}
