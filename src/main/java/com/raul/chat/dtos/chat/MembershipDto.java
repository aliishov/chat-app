package com.raul.chat.dtos.chat;

import com.raul.chat.models.chat.MemberRole;

import java.util.UUID;

public record MembershipDto(
        UUID userId,
        Long chatRoomId,
        MemberRole role
) {
}
