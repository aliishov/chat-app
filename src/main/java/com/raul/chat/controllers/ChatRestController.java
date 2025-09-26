package com.raul.chat.controllers;

import com.raul.chat.dtos.chat.ChatRoomDto;
import com.raul.chat.dtos.chat.MessageDto;
import com.raul.chat.dtos.chat.NewGroupChatDto;
import com.raul.chat.services.chat.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
@Validated
public class ChatRestController {

    private final ChatService chatService;

    @GetMapping("/rooms/{chatRoomId}/messages")
    public ResponseEntity<List<MessageDto>> getMessages(
            @PathVariable Long chatRoomId, @RequestParam(name = "since", required = false) Long lastSeenTimestamp
    ) {

        return ResponseEntity.ok(chatService.getMessagesSince(chatRoomId, lastSeenTimestamp));
    }

    @PostMapping("/rooms/create-group-chat")
    public ResponseEntity<ChatRoomDto> createGroupChat(@RequestBody @Valid NewGroupChatDto request) {
        ChatRoomDto response = chatService.createGroupChat(request);
        return ResponseEntity.ok(response);
    }
}
