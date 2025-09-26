package com.raul.chat.controllers;

import com.raul.chat.dtos.auth.MessageResponseDto;
import com.raul.chat.dtos.auth.RegisterRequestDto;
import com.raul.chat.dtos.auth.LoginResponseDto;
import com.raul.chat.dtos.auth.LoginRequestDto;
import com.raul.chat.dtos.auth.ForgotPasswordRequest;
import com.raul.chat.dtos.auth.ResetPasswordRequest;
import com.raul.chat.dtos.auth.EmailRequestDto;
import com.raul.chat.dtos.auth.RefreshTokenRequest;
import com.raul.chat.dtos.otp.OtpDto;
import com.raul.chat.services.auth.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Validated
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<MessageResponseDto> register(@Valid @RequestBody RegisterRequestDto request) {
        return ResponseEntity.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDto> login(@Valid @RequestBody LoginRequestDto request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/email-confirm")
    public ResponseEntity<MessageResponseDto> confirmEmail(@RequestBody @Valid OtpDto otp,
                                                           @RequestParam String token) {
        return ResponseEntity.ok(authService.confirmEmail(token, otp));
    }

    @PostMapping("/password-forgot")
    public ResponseEntity<MessageResponseDto> forgotPassword(@RequestBody @Valid ForgotPasswordRequest request) {
        return ResponseEntity.ok(authService.forgotPassword(request));
    }

    @PostMapping("/password-reset")
    public ResponseEntity<MessageResponseDto> resetPassword(@RequestBody @Valid ResetPasswordRequest request) {
        return ResponseEntity.ok(authService.resetPassword(request));
    }

    @PostMapping("/email/resend-confirmation")
    public ResponseEntity<MessageResponseDto> resendConfirmation(@RequestBody @Valid EmailRequestDto request) {
        return ResponseEntity.ok(authService.resendConfirmation(request));
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<LoginResponseDto> refreshToken(@RequestBody @Valid RefreshTokenRequest request) {
        return ResponseEntity.ok(authService.refreshToken(request));
    }
}
