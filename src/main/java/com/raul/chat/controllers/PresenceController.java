package com.raul.chat.controllers;

import com.raul.chat.dtos.auth.UserDto;
import com.raul.chat.services.chat.PresenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

import java.util.List;

@Controller
@RequiredArgsConstructor
@Slf4j
public class PresenceController {

    private final PresenceService presenceService;

    @MessageMapping("/online-users")
    public List<UserDto> getOnlineUsers() {
        log.info("Getting online users");
        return presenceService.getOnlineUsers();
    }
}
