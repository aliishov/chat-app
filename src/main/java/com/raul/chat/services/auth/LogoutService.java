package com.raul.chat.services.auth;

import com.raul.chat.models.user.Status;
import com.raul.chat.models.user.TokenType;
import com.raul.chat.repositories.auth.JwtTokenRepository;
import com.raul.chat.services.chat.PresenceService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LogoutService implements LogoutHandler {

    private final JwtTokenRepository jwtTokenRepository;
    private final SimpMessagingTemplate simpMessagingTemplate;
    private final JwtService jwtService;
    private final PresenceService presenceService;

    @Override
    public void logout(HttpServletRequest request,
                       HttpServletResponse response,
                       Authentication authentication) {
        final String authHeader = request.getHeader("Authorization");
        final String token;

        if (authHeader == null || !authHeader.startsWith("Bearer ")) return;

        token = authHeader.substring(7);

        jwtTokenRepository.findByToken(token).ifPresent(accessJwtToken -> {
            UUID userId = accessJwtToken.getUserId();

            accessJwtToken.setIsRevoked(true);
            accessJwtToken.setExpiresAt(LocalDateTime.now().minusMinutes(10));
            jwtTokenRepository.save(accessJwtToken);

            jwtTokenRepository.findByTokenTypeAndUserIdAndIsRevoked(
                    TokenType.REFRESH_TOKEN.name(), userId, false
            ).ifPresent(refreshJwtToken -> {
                refreshJwtToken.setIsRevoked(true);
                refreshJwtToken.setExpiresAt(LocalDateTime.now().minusMinutes(10));
                jwtTokenRepository.save(refreshJwtToken);
            });

            UUID tokenUserId = jwtService.extractUserId(token);
            if (tokenUserId.equals(userId)) {
                presenceService.updateUserStatus(userId, Status.OFFLINE);
                simpMessagingTemplate.convertAndSend("/topic/online-users", tokenUserId);
            }
        });
    }
}
