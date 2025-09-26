package com.raul.chat.services.chat.redis;

import com.raul.chat.dtos.chat.MessageDto;
import com.raul.chat.models.chat.MessageRecipient;
import com.raul.chat.models.user.Status;
import com.raul.chat.models.user.User;
import com.raul.chat.repositories.auth.UserRepository;
import com.raul.chat.repositories.chat.MessageRecipientRepository;
import com.raul.chat.services.chat.NotificationService;
import com.raul.chat.services.utils.UserUtils;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.KeyExpirationEventMessageListener;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class RedisKeyExpirationListener extends KeyExpirationEventMessageListener {

    private final NotificationService notificationService;
    private final MessageRecipientRepository messageRecipientRepository;
    private final UserRepository userRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final UserUtils userUtils;


    public RedisKeyExpirationListener(RedisMessageListenerContainer listenerContainer,
                                      NotificationService notificationService,
                                      MessageRecipientRepository messageRecipientRepository,
                                      UserRepository userRepository,
                                      RedisTemplate<String, Object> redisTemplate, UserUtils userUtils) {
        super(listenerContainer);
        this.notificationService = notificationService;
        this.messageRecipientRepository = messageRecipientRepository;
        this.userRepository = userRepository;
        this.redisTemplate = redisTemplate;
        this.userUtils = userUtils;
    }

    @Async
    @Override
    public void onMessage(Message message, byte[] pattern) {
        String expiredKey = message.toString();
        log.info("Redis key expired: {}", expiredKey);

        // MESSAGE DELIVERY
        if (expiredKey.startsWith("message:delivery:")) {
            handleMessageDeliveryExpiration(expiredKey);
        }

        // USER STATUS
        if (expiredKey.startsWith("user:status:")) {
            handleUserStatusExpiration(expiredKey);
        }
    }

    private void handleMessageDeliveryExpiration(String expiredKey) {
        String keySuffix = expiredKey.replace("message:delivery:", "");
        String[] parts = keySuffix.split(":");
        if (parts.length != 2) {
            log.error("Invalid Redis key format: {}", expiredKey);
            return;
        }

        UUID recipientId = UUID.fromString(parts[1]);

        MessageDto messageDto = (MessageDto) redisTemplate.opsForValue().get(expiredKey);
        if (messageDto == null) {
            log.warn("MessageDto not found in Redis for key {}", expiredKey);
            return;
        }

        User sender = userRepository.findById(messageDto.senderId())
                .orElseThrow(() -> userUtils.throwUserNotFoundException("ID", messageDto.senderId().toString()));

        MessageRecipient recipient = messageRecipientRepository.findByRecipient(recipientId)
                .orElseThrow(() -> new EntityNotFoundException("Recipient not found in Redis for key " + expiredKey));

        if (recipient == null) {
            log.warn("Recipient {} not found for message {}", recipientId, messageDto.messageId());
            return;
        }

        if (expiredKey.startsWith("message:delivery:")) {
            String messageId = expiredKey.replace("message:delivery:", "");
            log.warn("Message {} not delivered within TTL, sending notification...", messageId);
            notificationService.sendNotification(sender, messageDto, List.of(recipient));
        }
    }

    private void handleUserStatusExpiration(String expiredKey) {
        String userIdStr = expiredKey.replace("user:status:", "");
        try {
            UUID userId = UUID.fromString(userIdStr);
            userRepository.findById(userId).ifPresent(user -> {
                user.setStatus(Status.OFFLINE);
                userRepository.save(user);
                log.info("User {} marked OFFLINE due to Redis TTL expiration", userId);
            });
        } catch (IllegalArgumentException e) {
            log.error("Invalid UUID in expired key: {}", expiredKey, e);
        }
    }
}
