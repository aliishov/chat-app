package com.raul.chat.services.chat;

import com.raul.chat.dtos.auth.UserDto;
import com.raul.chat.models.user.Status;
import com.raul.chat.repositories.auth.UserRepository;
import com.raul.chat.services.utils.UserUtils;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PresenceService {
    static String STATUS_KEY_PREFIX = "user:status:";
    static String ONLINE_USERS_KEY = "online:users";
    static long TTL_SECONDS = 60;

    RedisTemplate<String, Object> redisTemplate;
    UserRepository userRepository;
    UserUtils userUtils;

    public void updateUserStatus(UUID userId, Status status) {
        String key = STATUS_KEY_PREFIX + userId;

        if (status == Status.ONLINE) {
            redisTemplate.opsForValue().set(key, "ONLINE", TTL_SECONDS, TimeUnit.SECONDS);
            redisTemplate.opsForSet().add(ONLINE_USERS_KEY, userId.toString());
            log.info("User {} marked ONLINE in Redis", userId);
        } else {
            redisTemplate.delete(key);
            redisTemplate.opsForSet().remove(ONLINE_USERS_KEY, userId.toString());
            log.info("User {} marked OFFLINE in Redis", userId);
        }
    }

    public List<UserDto> getOnlineUsers() {
        Set<Object> onlineUserIds = redisTemplate.opsForSet().members(ONLINE_USERS_KEY);
        if (onlineUserIds == null || onlineUserIds.isEmpty()) {
            return List.of();
        }

        return onlineUserIds.stream()
                .map(Object::toString)
                .map(UUID::fromString)
                .map(id -> userRepository.findById(id)
                        .map(userUtils::convertToUserDto)
                        .orElse(null))
                .filter(Objects::nonNull)
                .toList();
    }

    public boolean isOnline(UUID userId) {
        String key = STATUS_KEY_PREFIX + userId;
        String status = (String) redisTemplate.opsForValue().get(key);
        return "ONLINE".equals(status);
    }
}
