package com.raul.chat.repositories.chat;

import com.raul.chat.models.chat.ChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

    @Query(value = "SELECT cr FROM chat_rooms cr " +
                   "JOIN chat_room_memberships m1 ON cr.id = m1.chat_room_id " +
                   "JOIN chat_room_memberships m2 ON cr.id = m2.chat_room_id " +
                   "WHERE cr.type = 'PERSONAL' " +
                   "AND m1.user_id = :senderId AND m2.user_id = :recipientId", nativeQuery = true)
    Optional<ChatRoom> findPersonalChatRoom(@Param("senderId") UUID senderId, @Param("recipientId") UUID recipientId);
}
