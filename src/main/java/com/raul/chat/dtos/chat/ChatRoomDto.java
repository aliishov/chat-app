package com.raul.chat.dtos.chat;

import java.util.List;

public record ChatRoomDto(
        Long chatRoomId,
        String chartName,
        Integer participantsCount,
        List<MembershipDto> memberships
) {
}
