package com.raul.chat.repositories.chat;

import com.raul.chat.models.chat.MessageRecipient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface MessageRecipientRepository extends JpaRepository<MessageRecipient,Long> {

    @Query(value = "SELECT * FROM message_recipients mr " +
                   "WHERE mr.recipient_id = :recipientId", nativeQuery = true)
    Optional<MessageRecipient> findByRecipient(@Param("recipientId") UUID recipientId);
}
