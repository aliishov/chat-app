package com.raul.chat.repositories.chat;

import com.raul.chat.models.chat.ChatRoomMembership;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatRoomMembershipRepository extends JpaRepository<ChatRoomMembership, Long> {

    @Query(value = "SELECT * FROM chat_room_memberships crm WHERE chat_room_id = :chatRoomId", nativeQuery = true)
    List<ChatRoomMembership> findByChatRoomId(@Param("chatRoomId") Long chatRoomId);
}
