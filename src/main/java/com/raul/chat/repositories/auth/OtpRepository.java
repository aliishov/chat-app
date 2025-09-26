package com.raul.chat.repositories.auth;

import com.raul.chat.models.otp.OTPCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OtpRepository extends JpaRepository<OTPCode, Long> {

    @Query(value = "SELECT o.otp_code FROM otp o " +
                   "WHERE o.user_id = :userId " +
                   "AND o.otp_code = :otp AND o.expires_at > :now",
            nativeQuery = true)
    Optional<String> findValidOtp(@Param("userId") UUID userId,
                                  @Param("otp") String otp,
                                  @Param("now") LocalDateTime now);
}
