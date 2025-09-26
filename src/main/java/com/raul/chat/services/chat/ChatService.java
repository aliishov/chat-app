package com.raul.chat.services.chat;

import com.raul.chat.dtos.chat.RecipientDto;
import com.raul.chat.dtos.chat.MessageDto;
import com.raul.chat.dtos.chat.NewGroupChatDto;
import com.raul.chat.dtos.chat.NewMessageDto;
import com.raul.chat.dtos.chat.UpdateMessageStatusDto;
import com.raul.chat.dtos.chat.ChatRoomDto;
import com.raul.chat.models.chat.MessageRecipient;
import com.raul.chat.models.chat.ChatRoom;
import com.raul.chat.models.chat.Message;
import com.raul.chat.models.chat.MessageStatus;
import com.raul.chat.models.chat.ChatRoomType;
import com.raul.chat.models.chat.MemberRole;
import com.raul.chat.models.chat.ChatRoomMembership;
import com.raul.chat.models.user.User;
import com.raul.chat.repositories.chat.ChatRoomMembershipRepository;
import com.raul.chat.repositories.chat.ChatRoomRepository;
import com.raul.chat.repositories.chat.MessageRepository;
import com.raul.chat.repositories.auth.UserRepository;
import com.raul.chat.services.chat.redis.DeliveryTrackerService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {
    private static final String GROUP_CHAT_TOPIC = "/topic/chat-room.{id}";
    private static final String PERSONAL_CHAT_TOPIC = "/user/{id}/queue/messages";
    private static final String SYSTEM_USER_EMAIL = "system@chat";

    private final UserRepository userRepository;
    private final MessageRepository messageRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomMembershipRepository chatRoomMembershipRepository;
    private final MessageMapper  messageMapper;
    private final SimpMessagingTemplate messagingTemplate;
    private final MembershipMapper membershipMapper;
    private final NotificationService notificationService;
    private final DeliveryTrackerService deliveryTrackerService;

    // TODO refactor
    @Transactional
    public void sendMessage(NewMessageDto newMessageDto) {
        log.info("Sending a message to the chat");

        // Get the sender User
        User sender = userRepository.findById(newMessageDto.senderId())
                .orElseThrow(() -> {
                    log.warn("No sender found with id {}", newMessageDto.senderId());
                    return new EntityNotFoundException("Sender not found: " + newMessageDto.senderId());
                });

        // Find or create chat room
        ChatRoom chatRoom;
        User recipient = null;
        UUID recipientId =  newMessageDto.recipientId();
        if (newMessageDto.chatRoomId() == null) {
            assert recipientId != null;
            recipient = userRepository.findById(recipientId)
                .orElseThrow(() -> {
                    log.warn("No recipient found with id {}", recipientId);
                    return new EntityNotFoundException("Recipient not found: " + recipientId);
                });

            chatRoom = findOrCreatePersonalChatRoom(sender, recipient);
        } else {
            chatRoom = chatRoomRepository.findById(newMessageDto.chatRoomId())
                .orElseThrow(() -> {
                    log.warn("No chat room found with id {}", newMessageDto.chatRoomId());
                    return new EntityNotFoundException("ChatRoom not found: " + newMessageDto.chatRoomId());
                });
        }

        // Create Message entity
        Message message = Message.builder()
                .content(newMessageDto.content())
                .sender(sender)
                .chatRoom(chatRoom)
                .sentAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();

        // Determine recipients
        List<User> recipients;
        if (chatRoom.getType() == ChatRoomType.PERSONAL) {
            if (recipient == null) {
                assert recipientId != null;
                recipient = userRepository.findById(recipientId)
                        .orElseThrow(() -> {
                            log.warn("No recipient found with id {}", recipientId);
                            return new EntityNotFoundException("Recipient not found: " + recipientId);
                        });
            }
            assert recipient != null;
            recipients = List.of(recipient);
        } else {
            recipients = chatRoomMembershipRepository.findByChatRoomId(chatRoom.getId()).stream()
                    .map(ChatRoomMembership::getUser)
                    .filter(user -> !user.getId().equals(sender.getId()))
                    .toList();
        }

        // Create message recipients
        List<MessageRecipient> messageRecipients = createMessageRecipients(recipients,  message);

        // Save message with recipients
        message.setRecipients(messageRecipients);
        message = messageRepository.save(message);

        // Convert entity to DTO
        MessageDto messageDto = messageMapper.toMessageDto(message);

        // Selecting the message destination
        String destination = resolveDestination(messageDto, message.getChatRoom().getType());

        // Sending the message
        sendMessage(destination, messageDto);

        // Sending notifications
        notificationService.sendNotification(sender, messageDto, messageRecipients);
    }

    public List<MessageDto> getMessagesSince(Long chatRoomId, Long lastSeenTimestamp) {
        log.info("Getting unread messages from the chat room {}", chatRoomId);
        Instant since = (lastSeenTimestamp != null)
                ? Instant.ofEpochMilli(lastSeenTimestamp)
                : Instant.EPOCH;

        return messageRepository.findByChatRoomIdAndCreatedAtAfter(chatRoomId, since).stream()
                .map(messageMapper::toMessageDto)
                .toList();
    }

    @Transactional
    public ChatRoomDto createGroupChat(NewGroupChatDto groupChatDto) {
        log.info("Creating group chat: {}", groupChatDto.groupName());

        // Locking for participants
        List<User> participants = userRepository.findAllById(groupChatDto.participantIds());
        if (participants.size() != groupChatDto.participantIds().size()) {
            log.error("Participant and participants do not match");
            throw new EntityNotFoundException("Some participants not found");
        }

        // Get the creator user
        User creator = participants.stream()
                .filter(user -> user.getId().equals(groupChatDto.creatorId()))
                .findFirst()
                .orElseThrow(() -> {
                    log.warn("No creator found with id {}", groupChatDto.creatorId());
                    return new EntityNotFoundException("Creator not found");
                });

        // Creating chat room
        ChatRoom chatRoom = ChatRoom.builder()
                .name(groupChatDto.groupName())
                .creator(creator)
                .type(ChatRoomType.GROUP)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
        ChatRoom finalChatRoom  = chatRoomRepository.save(chatRoom);

        // Creating memberships
        List<ChatRoomMembership> memberships = participants.stream()
                .map(participant -> ChatRoomMembership.builder()
                        .user(participant)
                        .chatRoom(finalChatRoom)
                        .role(participant.getId().equals(creator.getId()) ? MemberRole.ADMIN : MemberRole.MEMBER)
                        .joinedAt(OffsetDateTime.now())
                        .build())
                .toList();
        chatRoomMembershipRepository.saveAll(memberships);

        // Creating system message
        Message systemMessage = Message.builder()
                .content("Group " + chatRoom.getName() + " created by "
                        + creator.getFirstName() + " " + creator.getLastName())
                .sender(getSystemUser())
                .chatRoom(chatRoom)
                .sentAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();

        // Determine recipients
        List<User> recipients = chatRoomMembershipRepository.findByChatRoomId(chatRoom.getId()).stream()
                .map(ChatRoomMembership::getUser)
                .filter(user -> !user.getId().equals(creator.getId()))
                .toList();

        // Create message recipients
        List<MessageRecipient> messageRecipients = createMessageRecipients(recipients, systemMessage);

        // Save message with recipients
        systemMessage.setRecipients(messageRecipients);
        systemMessage = messageRepository.save(systemMessage);

        // Convert entity to DTO
        MessageDto messageDto = messageMapper.toMessageDto(systemMessage);

        // Resolving the message destination
        String destination = resolveDestination(messageDto, ChatRoomType.GROUP);

        // Sending the message
        sendMessage(destination, messageDto);

        // TODO Implement using notification Service
        // Sending notifications
        notificationService.sendNotification(creator, messageDto, messageRecipients);

        return new ChatRoomDto(messageDto.chatRoomId(), chatRoom.getName(), participants.size(), memberships.stream()
                .map(membershipMapper::toMembershipDto).toList());
    }

    @Transactional
    public void updateMessageStatus(UpdateMessageStatusDto updateDto) {
        log.info("Updating message with ID {} status to {}", updateDto.messageId(), updateDto.status());
        MessageStatus status = updateDto.status();
        UUID recipientId = updateDto.recipientId();

        // Find a message
        Message message = messageRepository.findById(updateDto.messageId())
                .orElseThrow(() -> {
                    log.error("Message with ID {} not found", updateDto.messageId());
                    return new EntityNotFoundException("Message not found");
                });

        // Find a recipient
        MessageRecipient recipient = message.getRecipients().stream()
                .filter(r -> r.getRecipient().getId().equals(recipientId))
                .findFirst()
                .orElseThrow(() -> {
                    log.warn("Recipient with ID {} not found", recipientId);
                    return new EntityNotFoundException("Recipient not found in message");
                });

        // Update message status
        recipient.setStatus(status);
        switch (status) {
            case DELIVERED -> {
                recipient.setDeliveredAt(OffsetDateTime.now());
                deliveryTrackerService.markDelivered(message.getId(), recipientId);
            }
            case READ -> recipient.setReadAt(OffsetDateTime.now());
        }

        message.setUpdatedAt(OffsetDateTime.now());
        message = messageRepository.save(message);

        MessageDto messageDto = messageMapper.toMessageDto(message);

        // Selecting the message destination
        String destination = resolveDestination(messageDto, message.getChatRoom().getType());

        // Sending the message
        sendMessage(destination, messageDto);
    }

    private ChatRoom findOrCreatePersonalChatRoom(User sender, User recipient) {
        log.info("Finding personal chat room for users {} {}", sender.getId(), recipient.getId());
        Optional<ChatRoom> existingRoom = chatRoomRepository.findPersonalChatRoom(sender.getId(), recipient.getId());

        if (existingRoom.isPresent()) {
            log.info("Found personal chat room for users {} {}", sender.getId(), recipient.getId());
            return existingRoom.get();
        }

        // Creating new chatRoom
        log.info("Creating personal chat room for users {} {}", sender.getId(), recipient.getId());
        ChatRoom chatRoom = ChatRoom.builder()
                .name(sender.getFirstName() + " & " + recipient.getFirstName())
                .creator(sender)
                .type(ChatRoomType.PERSONAL)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
        chatRoom = chatRoomRepository.save(chatRoom);

        // Creating memberships
        log.info("Creating memberships for chat room {}", chatRoom.getId());
        ChatRoomMembership senderMembership = ChatRoomMembership.builder()
                .user(sender)
                .chatRoom(chatRoom)
                .role(MemberRole.MEMBER)
                .joinedAt(OffsetDateTime.now())
                .build();
        ChatRoomMembership recipientMembership = ChatRoomMembership.builder()
                .user(recipient)
                .chatRoom(chatRoom)
                .role(MemberRole.MEMBER)
                .joinedAt(OffsetDateTime.now())
                .build();
        List<ChatRoomMembership> memberships = List.of(senderMembership, recipientMembership);
        chatRoomMembershipRepository.saveAll(memberships);

        return chatRoom;
    }

    private String resolveDestination(MessageDto messageDto, ChatRoomType type) {
        log.info("Resolving message with ID {} and chat room type {}", messageDto.messageId(), type);
        return switch (type) {
            case PERSONAL -> PERSONAL_CHAT_TOPIC.replace("{id}", messageDto.recipients()
                    .get(0).recipientId().toString());
            case GROUP -> GROUP_CHAT_TOPIC.replace("{id}", messageDto.chatRoomId().toString());
        };
    }

    private User getSystemUser() {
        return userRepository.findByEmail(SYSTEM_USER_EMAIL)
                .orElseThrow(() -> new EntityNotFoundException("System user not found"));
    }

    private void sendMessage(String destination, MessageDto messageDto) {
        try {
            log.info("Sending message to chat room {}", messageDto.chatRoomId());
            messagingTemplate.convertAndSend(destination, messageDto);
            for (RecipientDto recipientDto : messageDto.recipients()) {
                deliveryTrackerService.trackMessage(messageDto, recipientDto.recipientId());
            }
        } catch (Exception e) {
            log.error("Error while sending a message: {}", messageDto);
            log.error("Failed to send message to {}: {}", destination, e.getMessage(), e);
        }
    }

    private List<MessageRecipient> createMessageRecipients(List<User> recipients ,Message message) {
        log.info("Creating message recipient list for message {}", message.getId());
        return recipients.stream()
                .map(recipient -> MessageRecipient.builder()
                        .message(message)
                        .recipient(recipient)
                        .status(MessageStatus.SENT)
                        .build())
                .toList();
    }
}
