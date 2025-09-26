package com.raul.chat.services.chat;

import com.raul.chat.dtos.chat.MembershipDto;
import com.raul.chat.models.chat.ChatRoomMembership;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MembershipMapper {

    public MembershipDto toMembershipDto(ChatRoomMembership membership) {
        return new MembershipDto(
                membership.getUser().getId(),
                membership.getChatRoom().getId(),
                membership.getRole()
        );
    }
}
