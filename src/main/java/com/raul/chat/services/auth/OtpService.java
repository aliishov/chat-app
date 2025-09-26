package com.raul.chat.services.auth;

import com.raul.chat.models.otp.OTPCode;
import com.raul.chat.repositories.auth.OtpRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OtpService {

    private static final SecureRandom secureRandom = new SecureRandom();
    private final OtpRepository otpRepository;

    public String generateOtp(UUID userId) {
        int number = secureRandom.nextInt(1_000_000);
        String otpCode = String.format("%06d", number);

        OTPCode otp = OTPCode
                .builder()
                .otpCode(otpCode)
                .userId(userId)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .build();

        otpRepository.save(otp);

        return otpCode;
    }

    public boolean validateOtp(UUID userId, String otp) {
        return otpRepository.findValidOtp(userId, otp, LocalDateTime.now())
                .isPresent();
    }
}
