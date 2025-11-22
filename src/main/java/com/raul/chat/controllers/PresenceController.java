package com.raul.chat.controllers;

import com.raul.chat.dtos.auth.UserDto;
import com.raul.chat.services.chat.PresenceService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

import java.util.List;

@Controller
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PresenceController {

    PresenceService presenceService;

    @MessageMapping("/online-users")
    public List<UserDto> getOnlineUsers() {
        log.info("Getting online users");
        return presenceService.getOnlineUsers();
    }
}
