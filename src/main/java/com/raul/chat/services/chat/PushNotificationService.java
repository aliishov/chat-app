package com.raul.chat.services.chat;

import com.raul.chat.dtos.chat.NotificationDto;
import com.raul.chat.services.auth.UserDevicesService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PushNotificationService {

    private final UserDevicesService userDevicesService;

    @Async
    public void sendPush(UUID recipientId, NotificationDto notificationDto) {
        List<String> deviceTokens = userDevicesService.getOfflineDeviceTokensByUserId(recipientId);

        for (String deviceToken : deviceTokens) {
            Message message = Message.builder()
                    .setToken(deviceToken)
                    .setNotification(Notification.builder()
                            .setTitle(notificationDto.userDto().firstName())
                            .setBody(notificationDto.messageContent())
                            .build())
                    .build();

            try {
                String response = FirebaseMessaging.getInstance().send(message);
                log.info("Sent message: {}", response);
            } catch (Exception e) {
                log.error("Error while sending push message", e);
            }
        }
    }
}
