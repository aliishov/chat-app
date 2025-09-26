package com.raul.chat.models.chat;

import com.raul.chat.models.user.User;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Id;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Column;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.CascadeType;
import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity
@Table(name = "messages")
public class Message {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "content",  nullable = false, columnDefinition = "TEXT")
    String content;

    @ManyToOne
    @JoinColumn(name = "sender_id", nullable = false)
    User sender;

    @OneToMany(mappedBy = "message", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MessageRecipient> recipients = new ArrayList<>();

    @ManyToOne
    @JoinColumn(name = "chat_room_id")
    ChatRoom chatRoom;

    @Column(name = "sent_at", nullable = false, updatable = false)
    OffsetDateTime sentAt;

    @Column(name = "updated_at")
    OffsetDateTime updatedAt;
}
