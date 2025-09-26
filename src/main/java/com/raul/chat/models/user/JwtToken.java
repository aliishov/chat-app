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
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity
@Table(name = "jwt_tokens")
public class JwtToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "token", nullable = false, unique = true)
    String token;

    @Enumerated(EnumType.STRING)
    @Column(name = "token_type",  nullable = false)
    TokenType tokenType;

    @Column(name = "user_id", nullable = false, updatable = false)
    UUID userId;

    @Column(name = "created_at", updatable = false, nullable = false)
    LocalDateTime createdAt;

    @Column(name = "expires_at", updatable = false, nullable = false)
    LocalDateTime expiresAt;

    @Column(name = "is_revoked",  nullable = false)
    Boolean isRevoked;
}
