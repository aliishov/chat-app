package com.raul.chat.services.redis;

import com.raul.chat.models.user.TokenType;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE)
public class JwtTokenTrackService {
    @Value("${jwt.expiration}")
    Long jwtExpiration;

    @Value("${jwt.refresh.expiration}")
    Long refreshJwtExpiration;

    static final String JWT_PREFIX = "token:jwt:";
    static final String REFRESH_PREFIX = "token:refresh:";
    static final String REVOKED_SUFFIX = ":revoked";

    final RedisTemplate<String, Object> redisTemplate;

    public void saveToken(UUID userId, String token, TokenType tokenType) {
        String key = getTokenKey(userId, token, tokenType);
        long TTL = getTTLSeconds(tokenType);

        redisTemplate.opsForValue().set(key, token, TTL, TimeUnit.SECONDS);
        log.info("Saved {} token for user {} in Redis with TTL {} sec", tokenType, userId, TTL);
    }

    public boolean isTokenValid(String token, UUID userId, TokenType tokenType) {
        String key = getTokenKey(userId, token, tokenType);
        String revokedKey = key + REVOKED_SUFFIX;

        Boolean exists = redisTemplate.hasKey(key);
        Boolean isRevoked = redisTemplate.hasKey(revokedKey);

        boolean valid = exists && !isRevoked;
        log.info("Token {} valid: {}", token, valid);
        return valid;
    }

    public void revokeToken(UUID userId, String token, TokenType type) {
        String key = getTokenKey(userId, token, type);
        String revokedKey = key + REVOKED_SUFFIX;

        long TTL = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        if (TTL > 0) {
            redisTemplate.opsForValue().set(revokedKey, true, TTL, TimeUnit.SECONDS);
        }

        log.info("Revoked {} token for user {}", type, userId);
    }

    public void revokeAllTokens(UUID userId, TokenType tokenType) {
        String prefix = (tokenType == TokenType.REFRESH_TOKEN) ? REFRESH_PREFIX : JWT_PREFIX;
        Set<String> keys = redisTemplate.keys(prefix + userId + ":*");

        if (!keys.isEmpty()) {
            for (String key : keys) {
                redisTemplate.opsForValue().set(key + REVOKED_SUFFIX, true, 1, TimeUnit.HOURS);
            }
            log.info("Revoked all tokens for user {}", userId);
        }
    }

    private String getTokenKey(UUID userId, String token, TokenType type) {
        String prefix = (type == TokenType.REFRESH_TOKEN) ? REFRESH_PREFIX : JWT_PREFIX;
        return prefix + userId + ":" + token;
    }

    private long getTTLSeconds(TokenType tokenType) {
        long TTL;

        switch (tokenType) {
            case REFRESH_TOKEN -> TTL = refreshJwtExpiration / 1000;
            case ACCESS_TOKEN -> TTL = jwtExpiration / 1000;
            default -> TTL = 0;
        }

        return Math.max(TTL, 0);
    }
}
