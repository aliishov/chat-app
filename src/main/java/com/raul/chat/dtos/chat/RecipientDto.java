package com.raul.chat.dtos.chat;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.raul.chat.models.chat.MessageStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record RecipientDto(
        UUID recipientId,
        MessageStatus status,

        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
        OffsetDateTime deliveredAt,

        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
        OffsetDateTime readAt
) {
}
