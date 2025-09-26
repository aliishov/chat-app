package com.raul.chat.repositories.chat;

import com.raul.chat.models.chat.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    @Query(
            value = "SELECT * FROM messages m " +
                    "WHERE m.chat_room_id = :chatRoomId " +
                    "AND m.sent_at > :since " +
                    "ORDER BY m.sent_at",
            nativeQuery = true
    )
    List<Message> findByChatRoomIdAndCreatedAtAfter(@Param("chatRoomId") Long chatRoomId, @Param("since") Instant since);
}
