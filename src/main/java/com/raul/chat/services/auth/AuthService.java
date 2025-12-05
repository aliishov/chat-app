package com.raul.chat.services.auth;

import com.raul.chat.dtos.auth.MessageResponseDto;
import com.raul.chat.dtos.auth.RegisterRequestDto;
import com.raul.chat.dtos.auth.LoginResponseDto;
import com.raul.chat.dtos.auth.LoginRequestDto;
import com.raul.chat.dtos.auth.UserDto;
import com.raul.chat.dtos.auth.ForgotPasswordRequest;
import com.raul.chat.dtos.auth.ResetPasswordRequest;
import com.raul.chat.dtos.auth.EmailRequestDto;
import com.raul.chat.dtos.auth.RefreshTokenRequest;
import com.raul.chat.dtos.otp.OtpDto;
import com.raul.chat.models.mail.EmailNotificationSubject;
import com.raul.chat.models.user.Status;
import com.raul.chat.models.user.TokenType;
import com.raul.chat.models.user.User;
import com.raul.chat.repositories.auth.UserRepository;
import com.raul.chat.services.utils.UserUtils;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AuthService {

    @Value("${url.domain}")
    String URL_DOMAIN;

    final UserRepository userRepository;
    final PasswordEncoder passwordEncoder;
    final AuthenticationManager authenticationManager;
    final JwtService jwtService;
    final EmailSenderService emailSenderService;
    final TokenService tokenService;
    final OtpService otpService;
    final SimpMessagingTemplate messagingTemplate;
    final UserUtils userUtils;

    @Transactional
    public MessageResponseDto register(RegisterRequestDto request) {
        log.info("Registering a new user with email: {}", request.email());

        User newUser = userUtils.convertToNewUser(request);

        newUser = userRepository.save(newUser);
        String otpCode = otpService.generateOtp(newUser.getId());

        String token = tokenService.generateToken(newUser.getId(), TokenType.EMAIL_CONFIRMATION_TOKEN);
        String confirmationLink = URL_DOMAIN + "/auth/email/confirm?token=" + token;

        Map<String, String> placeholders = Map.of(
                "confirmation_link", confirmationLink,
                "otp_code", otpCode
        );

        emailSenderService.sendEmail(newUser.getEmail(), EmailNotificationSubject.EMAIL_CONFIRMATION_NOTIFICATION,
                placeholders);

        log.info("User with Email: {} registered successfully", newUser.getEmail());
        return new MessageResponseDto("User registered successfully");
    }

    @Transactional
    public LoginResponseDto login(LoginRequestDto request) {
        log.info("Login requested: {}", request.email());

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email(), request.password())
            );
        } catch (AuthenticationException e) {
            log.error("Authentication failed for email: {}", request.email(), e);
            throw new BadCredentialsException("Invalid email or password");
        }

        User user = (User) authentication.getPrincipal();

        user.setStatus(Status.ONLINE);
        user = userRepository.save(user);

        String accessToken = jwtService.generateToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);

        UserDto userDto = userUtils.convertToUserDto(user);

        messagingTemplate.convertAndSend("/topic/online-users", userDto);

        log.info("User with Email: {} successfully login", userDto.email());
        return new LoginResponseDto(accessToken, refreshToken);
    }

    @Transactional
    public MessageResponseDto confirmEmail(String token, OtpDto otp) {
        log.info("Confirming email with token: {}", token);

        UUID userId = tokenService.validateToken(token);

        if (!otpService.validateOtp(userId, otp.otpCode())) {
            log.warn("Invalid OTP: {} provided for User {}",  otp.otpCode(), userId);
            throw new IllegalArgumentException("Invalid OTP provided");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> userUtils.throwUserNotFoundException("ID", userId.toString()));

        user.setIsAuthenticated(true);

        userRepository.save(user);

        log.info("Email confirmed successfully for user with email: {}", user.getEmail());
        return new MessageResponseDto("Email confirmed successfully");
    }

    public MessageResponseDto forgotPassword(ForgotPasswordRequest request) {
        log.info("Processing forgot password for Email: {}", request.email());

        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> userUtils.throwUserNotFoundException("email", request.email()));

        String token = tokenService.generateToken(user.getId(), TokenType.FORGOT_PASSWORD_TOKEN);
        String resetLink = URL_DOMAIN + "/auth/password/reset?token=" + token;

        Map<String, String> placeholders = Map.of("reset_link", resetLink);

        emailSenderService.sendEmail(user.getEmail(), EmailNotificationSubject.FORGOT_PASSWORD, placeholders);

        log.info("Forgot password email sent to: {}", user.getEmail());
        return new MessageResponseDto("Forgot password email sent successfully");
    }

    public MessageResponseDto resetPassword(ResetPasswordRequest request) {
        log.info("Resetting password");

        if (!request.newPassword().equals(request.passwordRepeated())) {
            log.warn("New password and repeated password do not match");
            throw new IllegalArgumentException("New password and repeated password do not match");
        }

        UUID userId = tokenService.validateToken(request.token());

        User user = userRepository.findById(userId)
                .orElseThrow(() -> userUtils.throwUserNotFoundException("ID", userId.toString()));

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        return new MessageResponseDto("Password reset successfully");
    }

    public MessageResponseDto resendConfirmation(EmailRequestDto request) {
        log.info("Resending confirmation email");

        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> userUtils.throwUserNotFoundException("email", request.email()));

        String token = tokenService.generateToken(user.getId(), TokenType.EMAIL_CONFIRMATION_TOKEN);
        String confirmationLink = URL_DOMAIN + "/auth/email/confirm?token=" + token;

        Map<String, String> placeholders = Map.of("confirmation_link", confirmationLink);

        emailSenderService.sendEmail(user.getEmail(),
                EmailNotificationSubject.EMAIL_CONFIRMATION_NOTIFICATION, placeholders);

        log.info("Confirmation email sent to: {}", user.getEmail());
        return new MessageResponseDto("Confirmation email sent successfully");
    }

    public LoginResponseDto refreshToken(RefreshTokenRequest request) {
        log.info("Refreshing token");

        String token = request.refreshToken();
        String email = jwtService.extractUsername(token);
        if (email == null || jwtService.isTokenExpired(token)) {
            log.error("Invalid refresh token provided");
            throw new IllegalArgumentException("Refresh token is invalid or expired");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> userUtils.throwUserNotFoundException("email", email));

        String newAccessToken = jwtService.generateToken(user);

        log.info("Token refreshed successfully for user with Email: {}", user.getEmail());
        return new LoginResponseDto(newAccessToken, token);
    }
}
