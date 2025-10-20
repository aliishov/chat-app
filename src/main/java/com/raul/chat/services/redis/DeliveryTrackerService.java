package com.raul.chat.services.redis;

import com.raul.chat.dtos.chat.MessageDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeliveryTrackerService {
    private static final String PREFIX = "message:delivery:";
    private static final long TTL_SECONDS = 5;

    private final RedisTemplate<String, Object> redisTemplate;

    @Async
    public void trackMessage(MessageDto messageDto, UUID recipientId) {
        String key = PREFIX + messageDto.messageId() + ":" + recipientId;
        redisTemplate.opsForValue().set(key, messageDto, TTL_SECONDS, TimeUnit.SECONDS);
        log.info("Tracking delivery for message {} to user {} with TTL {}s",
                messageDto.messageId(), recipientId, TTL_SECONDS);
    }

    @Async
    public void markAsDelivered(Long messageId, UUID recipientId) {
        String key = PREFIX + messageId + ":" + recipientId;
        redisTemplate.delete(key);
        log.info("Message {} delivered, removed from Redis", messageId);
    }
}
