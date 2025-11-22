package com.raul.chat.services.chat;

import com.raul.chat.dtos.auth.UserDto;
import com.raul.chat.dtos.chat.MessageDto;
import com.raul.chat.dtos.chat.NotificationDto;
import com.raul.chat.models.chat.MessageRecipient;
import com.raul.chat.models.user.User;
import com.raul.chat.services.utils.UserUtils;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class NotificationService {
    static String NOTIFICATION_TOPIC = "/user/{id}/queue/notifications";

    SimpMessagingTemplate messagingTemplate;
    PresenceService presenceService;
    PushNotificationService pushNotificationService;
    UserUtils userUtils;

    @Async
    public void sendNotification(User sender, MessageDto messageDto, List<MessageRecipient> messageRecipients) {
        UserDto userDto = userUtils.convertToUserDto(sender);
        NotificationDto notificationDto = new  NotificationDto(userDto, messageDto.content());
        messageRecipients.forEach(mr -> {
            UUID recipientId = mr.getRecipient().getId();

            // Do not send notification to yourself
            if (recipientId.equals(sender.getId())) return;

            // Check the user status ONLINE/OFFLINE
            if (presenceService.isOnline(recipientId)) {
                log.info("User {} online, skip push, rely on WebSocket", recipientId);
                String notificationDestination = NOTIFICATION_TOPIC.replace("{id}", recipientId.toString());
                messagingTemplate.convertAndSend(notificationDestination, notificationDto);
            } else {
                log.info("User {} offline, send push notification", recipientId);
                pushNotificationService.sendPush(recipientId, notificationDto);
            }
        });
    }
}
