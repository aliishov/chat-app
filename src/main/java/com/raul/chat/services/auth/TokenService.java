package com.raul.chat.services.auth;

import com.raul.chat.models.user.Token;
import com.raul.chat.models.user.TokenType;
import com.raul.chat.repositories.auth.TokenRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class TokenService {

    TokenRepository tokenRepository;

    public String generateToken(UUID userId, TokenType tokenType) {
        log.info("Generating new token with type {}", tokenType.toString());

        String token = UUID.randomUUID().toString();

        Token tokenDomain = Token.builder()
                .token(token)
                .tokenType(tokenType)
                .expiresAt(LocalDateTime.now().plusMinutes(15))
                .confirmedAt(null)
                .userId(userId)
                .build();

        tokenRepository.save(tokenDomain);
        log.info("Token generated successfully: {}", token);

        return token;
    }

    public UUID validateToken(String token) {
        var tokenDomain = tokenRepository.findByToken(token)
                .orElseThrow(() -> new EntityNotFoundException("Token not found"));

        if (tokenDomain.getConfirmedAt() != null) {
            throw new IllegalArgumentException("Token already confirmed");
        }

        if (tokenDomain.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Token expired");
        }

        tokenDomain.setConfirmedAt(LocalDateTime.now());

        return tokenDomain.getUserId();
    }
}
