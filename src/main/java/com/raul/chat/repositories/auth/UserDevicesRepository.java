package com.raul.chat.repositories.auth;

import com.raul.chat.models.user.UserDevices;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserDevicesRepository extends JpaRepository<UserDevices, Long> {

    @Query(value = "SELECT ud.device_token FROM user_devices ud " +
                   "WHERE ud.user_id = :userId " +
                   "AND ud.is_online = FALSE",
            nativeQuery = true)
    List<String> findDeviceTokenByUserId(@Param("userId") UUID userId);

    @Query(value = "SELECT EXISTS " +
                   "(SELECT 1 FROM user_devices ud WHERE ud.user_id = :userId AND ud.id = :deviceId)",
            nativeQuery = true)
    boolean findByDeviceTokenAndUserId(@Param("userId") UUID userId,
                                       @Param("deviceId") Long deviceId);

    @Query(value = "SELECT * FROM user_devices ud WHERE ud.device_token = :deviceToken", nativeQuery = true)
    Optional<UserDevices> findByDeviceToken(@Param("deviceToken") String deviceToken);
}
