package com.raul.chat.services.auth;

import com.raul.chat.models.user.DeviceType;
import com.raul.chat.models.user.User;
import com.raul.chat.models.user.UserDevices;
import com.raul.chat.repositories.auth.UserDevicesRepository;
import com.raul.chat.repositories.auth.UserRepository;
import com.raul.chat.services.utils.UserUtils;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserDevicesService {

    private final UserDevicesRepository userDevicesRepository;
    private final UserRepository userRepository;
    private final UserUtils userUtils;

    @Async
    public void saveDevice(String deviceToken, DeviceType deviceType, UUID userId, boolean isOnline) {
        log.debug("Saving device {} {} for user {}", deviceToken, deviceType, userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> userUtils.throwUserNotFoundException("ID", userId.toString()));

        UserDevices userDevices = UserDevices.builder()
                .user(user)
                .deviceToken(deviceToken)
                .deviceType(deviceType)
                .isOnline(isOnline)
                .lastActive(OffsetDateTime.now())
                .build();

        userDevicesRepository.save(userDevices);
    }

    public List<String> getOfflineDeviceTokensByUserId(UUID userId) {
        return userDevicesRepository.findDeviceTokenByUserId(userId);
    }

    public boolean isDeviceExistForUser(UUID userId, Long deviceId) {
        return userDevicesRepository.findByDeviceTokenAndUserId(userId, deviceId);
    }

    public void updateDeviceStatus(Long disDeviceId, boolean status) {
        UserDevices userDevices = userDevicesRepository.findById(disDeviceId)
                .orElseThrow(() -> new EntityNotFoundException("Device not found"));
        if (!status) {
            userDevices.setLastActive(OffsetDateTime.now());
        }
        userDevices.setIsOnline(status);
        userDevicesRepository.save(userDevices);
    }

    public void deleteDevice(Long deviceId) {
        userDevicesRepository.deleteById(deviceId);
    }
}
