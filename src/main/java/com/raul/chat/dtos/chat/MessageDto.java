package com.raul.chat.dtos.chat;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record MessageDto(
        Long messageId,
        String content,
        UUID senderId,
        Long chatRoomId,
        List<RecipientDto> recipients,

        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
        OffsetDateTime sentAt
) {
}
