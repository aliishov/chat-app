package com.raul.chat.models.user;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Id;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Column;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity
@Table(name = "tokens")
public class Token {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "token", nullable = false, unique = true)
    String token;

    @Column(name = "token_type", nullable = false)
    @Enumerated(EnumType.STRING)
    TokenType tokenType;

    @Column(name = "expires_at", nullable = false, updatable = false)
    LocalDateTime expiresAt;

    @Column(name = "confirmed_at")
    LocalDateTime confirmedAt;

    @Column(name = "user_id", nullable = false, updatable = false)
    UUID userId;
}
