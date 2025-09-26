package com.raul.chat.dtos.chat;

import java.util.List;
import java.util.UUID;

public record NewGroupChatDto(
        String groupName,
        UUID creatorId,
        List<UUID> participantIds
) {
}
