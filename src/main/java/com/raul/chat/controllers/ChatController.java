package com.raul.chat.controllers;

import com.raul.chat.dtos.chat.NewMessageDto;
import com.raul.chat.dtos.chat.UpdateMessageStatusDto;
import com.raul.chat.services.chat.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @MessageMapping("/chat.send")
    public void sendMessage(@Payload NewMessageDto newMessageDto) {
        chatService.sendMessage(newMessageDto);
    }

    @MessageMapping("/update-status")
    public void updateStatus(@Payload UpdateMessageStatusDto updateDto) {
        chatService.updateMessageStatus(updateDto);
    }
}
