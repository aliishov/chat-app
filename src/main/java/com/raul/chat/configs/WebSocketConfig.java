package com.raul.chat.configs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.raul.chat.exceptions.InvalidTokenException;
import com.raul.chat.models.user.DeviceType;
import com.raul.chat.models.user.Status;
import com.raul.chat.models.user.TokenType;
import com.raul.chat.services.auth.JwtService;
import com.raul.chat.services.auth.UserDevicesService;
import com.raul.chat.services.chat.PresenceService;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.converter.DefaultContentTypeResolver;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.springframework.util.MimeTypeUtils.APPLICATION_JSON;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
@Slf4j
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtService jwtService;
    private final PresenceService presenceService;
    private final UserDetailsService userDetailsService;
    private final UserDevicesService userDevicesService;

    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler taskScheduler = new ThreadPoolTaskScheduler();
        taskScheduler.setPoolSize(5);
        taskScheduler.setThreadNamePrefix("wss-heartbeat-");
        taskScheduler.initialize();
        return taskScheduler;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws") // localhost:8080/ws
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue", "/user", "/chat-rooms")
                .setTaskScheduler(taskScheduler())
                .setHeartbeatValue(new long[]{5000, 5000});
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public boolean configureMessageConverters(List<MessageConverter> messageConverters) {
        DefaultContentTypeResolver resolver = new DefaultContentTypeResolver();
        resolver.setDefaultMimeType(APPLICATION_JSON);
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.setObjectMapper(new ObjectMapper());
        converter.setContentTypeResolver(resolver);
        messageConverters.add(converter);
        return false;
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(@Nullable Message<?> message, @Nullable MessageChannel channel) {
                StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
                UUID userId = validateToken(accessor);
                switch (Objects.requireNonNull(accessor.getCommand())) {
                    case CONNECT:
                        String conDeviceId = accessor.getFirstNativeHeader("x-device-id");
                        String deviceToken = accessor.getFirstNativeHeader("x-device-token");
                        String deviceType = accessor.getFirstNativeHeader("x-device-type");

                        assert conDeviceId != null;
                        if (!userDevicesService.isDeviceExistForUser(userId, Long.parseLong(conDeviceId))) {
                            assert deviceType != null;
                            userDevicesService.saveDevice(
                                    deviceToken,
                                    DeviceType.valueOf(deviceType.toUpperCase()),
                                    userId,
                                    true
                            );
                        } else {
                            userDevicesService.updateDeviceStatus(Long.parseLong(conDeviceId), true);
                        }

                        presenceService.updateUserStatus(userId, Status.ONLINE);
                        log.info("User {} connected", userId);
                        break;
                    case DISCONNECT:
                        String disDeviceId = accessor.getFirstNativeHeader("disDeviceId");
                        assert disDeviceId != null;
                        userDevicesService.updateDeviceStatus(Long.parseLong(disDeviceId), false);
                        presenceService.updateUserStatus(userId, Status.OFFLINE);
                        log.info("User {} disconnected", userId);
                        break;
                    case SUBSCRIBE:
                        log.info("Subscription received: {} with id: {}", accessor.getDestination(),
                                accessor.getSubscriptionId());
                        break;
                    case ERROR:
                        log.error("Error received: {}",
                                new String((byte[]) Objects.requireNonNull(message).getPayload()));
                        break;
                    case SEND:
                        log.info("Send received");
                        break;
                    default:
                        log.error("Invalid command: {}", accessor.getCommand());
                }

                log.info("Inbound message: {} to {}", accessor.getCommand(), accessor.getDestination());
                return message;
            }
        });
    }

    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(@Nullable Message<?> message, @Nullable MessageChannel channel) {
                StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
                log.info("Outgoing: {} to {}  payload: {}", accessor.getCommand(), accessor.getDestination(),
                        new String((byte[]) Objects.requireNonNull(message).getPayload()));
                return message;
            }

            @Override
            public void afterSendCompletion(@Nullable Message<?> message,
                                            @Nullable MessageChannel channel,
                                            boolean sent,
                                            Exception ex) {
                if (ex != null) {
                    log.error("Outbound error: {}", ex.getMessage());
                } else if (sent) {
                    log.info("Outbound sent: {}", message);
                    log.info("Message sent successfully through outbound channel.");
                }
            }
        });
    }

    private UUID validateToken(StompHeaderAccessor accessor) {
        try {
            String authHeader = accessor.getFirstNativeHeader("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) return null;
            String jwtToken = authHeader.substring(7);
            String username = jwtService.extractUsername(jwtToken);
            UUID userId = jwtService.extractUserId(jwtToken);
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);
            if (!jwtService.isTokenValid(jwtToken, userDetails, TokenType.ACCESS_TOKEN)) {
                log.warn("Invalid JWT Token for user: {}", username);
                throw new InvalidTokenException("Invalid JWT Token provided");
            }
            return userId;
        } catch (Exception e) {
            log.error("Token validation error: {}", e.getMessage());
            return null;
        }
    }
}
