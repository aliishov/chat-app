package com.raul.chat.controllers;

import com.raul.chat.dtos.auth.MessageResponseDto;
import com.raul.chat.dtos.auth.UserDto;
import com.raul.chat.dtos.chat.ChatRoomDto;
import com.raul.chat.dtos.chat.GroupChatRoleDto;
import com.raul.chat.dtos.chat.MessageDto;
import com.raul.chat.dtos.chat.NewGroupChatDto;
import com.raul.chat.models.user.User;
import com.raul.chat.services.chat.ChatService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
@Validated
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ChatRestController {

    ChatService chatService;

    @GetMapping("/rooms/{chatRoomId}/messages")
    public ResponseEntity<List<MessageDto>> getMessages(
            @PathVariable Long chatRoomId,
            @RequestParam(name = "since", required = false) Long lastSeenTimestamp,
            @AuthenticationPrincipal User user)
    {
        UUID userId = user.getId();
        List<MessageDto> response = chatService.getMessagesSince(chatRoomId, lastSeenTimestamp, userId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/rooms/create-group-chat")
    public ResponseEntity<ChatRoomDto> createGroupChat(@RequestBody @Valid NewGroupChatDto request) {
        ChatRoomDto response = chatService.createGroupChat(request);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/groups/{chatId}/user/{userId}")
    public ResponseEntity<UserDto> changeUserRole(@PathVariable Long chatId,
                                                  @PathVariable UUID userId,
                                                  @RequestBody @Valid GroupChatRoleDto request,
                                                  @AuthenticationPrincipal User user) {
        UUID adminId = user.getId();
        UserDto response = chatService.changeUserRole(chatId, userId, adminId, request);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/rooms/{chatId}/leave")
    public ResponseEntity<MessageResponseDto> leaveGroup(@PathVariable Long chatId,
                                                         @AuthenticationPrincipal User user) {
        UUID userId = user.getId();
        MessageResponseDto response = chatService.leaveGroup(chatId, userId);
        return ResponseEntity.accepted().body(response);
    }
}
