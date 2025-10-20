package com.raul.chat.services.auth;

import com.raul.chat.models.user.Status;
import com.raul.chat.models.user.TokenType;
import com.raul.chat.services.chat.PresenceService;
import com.raul.chat.services.redis.JwtTokenTrackService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LogoutService implements LogoutHandler {

    private final SimpMessagingTemplate simpMessagingTemplate;
    private final JwtService jwtService;
    private final PresenceService presenceService;
    private final JwtTokenTrackService jwtTokenTrackService;

    @Override
    public void logout(HttpServletRequest request,
                       HttpServletResponse response,
                       Authentication authentication) {
        final String authHeader = request.getHeader("Authorization");
        final String token;

        if (authHeader == null || !authHeader.startsWith("Bearer ")) return;

        token = authHeader.substring(7);
        UUID userId = jwtService.extractUserId(token);

        if (userId == null) return;

        jwtTokenTrackService.revokeAllTokens(userId, TokenType.REFRESH_TOKEN);
        jwtTokenTrackService.revokeAllTokens(userId, TokenType.ACCESS_TOKEN);

        presenceService.updateUserStatus(userId, Status.OFFLINE);
        simpMessagingTemplate.convertAndSend("/topic/online-users", userId);
    }
}
