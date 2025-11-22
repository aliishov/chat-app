package com.raul.chat.services.chat;

import com.raul.chat.dtos.auth.MessageResponseDto;
import com.raul.chat.dtos.auth.UserDto;
import com.raul.chat.dtos.chat.RecipientDto;
import com.raul.chat.dtos.chat.MessageDto;
import com.raul.chat.dtos.chat.NewGroupChatDto;
import com.raul.chat.dtos.chat.NewMessageDto;
import com.raul.chat.dtos.chat.UpdateMessageStatusDto;
import com.raul.chat.dtos.chat.ChatRoomDto;
import com.raul.chat.dtos.chat.GroupChatRoleDto;
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
import com.raul.chat.services.redis.DeliveryTrackerService;
import com.raul.chat.services.utils.UserUtils;
import jakarta.persistence.EntityNotFoundException;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ChatService {
    static final String GROUP_CHAT_TOPIC = "/topic/chat-room.{id}";
    static final String PERSONAL_CHAT_TOPIC = "/user/{id}/queue/messages";
    static final String SYSTEM_USER_EMAIL = "system@chat";

    final UserRepository userRepository;
    final MessageRepository messageRepository;
    final ChatRoomRepository chatRoomRepository;
    final ChatRoomMembershipRepository chatRoomMembershipRepository;
    final MessageMapper  messageMapper;
    final MembershipMapper membershipMapper;
    final SimpMessagingTemplate messagingTemplate;
    final NotificationService notificationService;
    final DeliveryTrackerService deliveryTrackerService;
    final UserUtils userUtils;

    User systemUser;

    @PostConstruct
    public void init() {
         systemUser = userRepository.findByEmail(SYSTEM_USER_EMAIL)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));
    }

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
                .orElseThrow(() -> userUtils.throwUserNotFoundException("ID", recipientId.toString()));

            chatRoom = findOrCreatePersonalChatRoom(sender, recipient);
        } else {
            chatRoom = chatRoomRepository.findById(newMessageDto.chatRoomId())
                .orElseThrow(() -> {
                    log.warn("No chat room found with id {}", newMessageDto.chatRoomId());
                    return new EntityNotFoundException("ChatRoom not found: " + newMessageDto.chatRoomId());
                });
        }

        // Determine recipients
        List<User> recipients;
        if (chatRoom.getType() == ChatRoomType.PERSONAL) {
            if (recipient == null) {
                assert recipientId != null;
                recipient = userRepository.findById(recipientId)
                        .orElseThrow(() -> userUtils.throwUserNotFoundException("ID", recipientId.toString()));
            }
            recipients = List.of(recipient);
        } else {
            recipients = chatRoomMembershipRepository.findByChatRoomId(chatRoom.getId()).stream()
                    .map(ChatRoomMembership::getUser)
                    .filter(user -> !user.getId().equals(sender.getId()))
                    .toList();
        }

        processSendMessage(sender, chatRoom, newMessageDto.content(), chatRoom.getType(), recipients, true);
    }

    public List<MessageDto> getMessagesSince(Long chatRoomId, Long lastSeenTimestamp, UUID userId) {
        log.info("Getting unread messages from the chat room {}", chatRoomId);

        chatRoomMembershipRepository
                .findByUserIdAndChatId(userId, chatRoomId)
                .orElseThrow(() -> new IllegalArgumentException("You are not a member of this chat"));

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
                .orElseThrow(() -> userUtils.throwUserNotFoundException("ID", groupChatDto.creatorId().toString()));

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

        String content = "Group " + chatRoom.getName() + " created by "
                + creator.getFirstName() + " " + creator.getLastName();

        // Determine recipients
        List<User> recipients = chatRoomMembershipRepository.findByChatRoomId(chatRoom.getId()).stream()
                .map(ChatRoomMembership::getUser)
                .filter(user -> !user.getId().equals(creator.getId()))
                .toList();

        processSendMessage(creator, finalChatRoom, content, chatRoom.getType(), recipients, true);

        return new ChatRoomDto(chatRoom.getId(), chatRoom.getName(), participants.size(), memberships.stream()
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
                deliveryTrackerService.markAsDelivered(message.getId(), recipientId);
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

    private void sendMessage(String destination, MessageDto messageDto) {
        try {
            log.info("Sending message to chat room {}", messageDto.chatRoomId());
            messagingTemplate.convertAndSend(destination, messageDto);
            for (RecipientDto recipientDto : messageDto.recipients()) {
                deliveryTrackerService.trackMessage(messageDto, recipientDto.recipientId());
            }
        } catch (Exception e) {
            log.error("Error while sending a message: {} {}", messageDto, e.getMessage(), e);
        }
    }

    private void processSendMessage(User sender, ChatRoom chatRoom, String content, ChatRoomType chatRoomType,
                                    List<User> recipients, boolean sendNotification) {
        Message message = Message.builder()
                .content(content)
                .sender(sender)
                .chatRoom(chatRoom)
                .sentAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();

        // Create message recipients
        List<MessageRecipient> messageRecipients = createMessageRecipients(recipients,  message);

        // Save message with recipients
        message.setRecipients(messageRecipients);
        message = messageRepository.save(message);

        // Convert entity to DTO
        MessageDto messageDto = messageMapper.toMessageDto(message);

        // Selecting the message destination
        String destination = resolveDestination(messageDto, chatRoomType);

        // Sending the message
        sendMessage(destination, messageDto);

        // Sending notifications
        if (sendNotification) {
            notificationService.sendNotification(sender, messageDto, messageRecipients);
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

    @Transactional
    public UserDto changeUserRole(Long chatId, UUID userId, UUID adminId, GroupChatRoleDto request) {
        log.info("Changing user role for user {}", userId);

        ChatRoom chatRoom = findChatRoomById(chatId);

        if (!chatRoom.getType().equals(ChatRoomType.GROUP)) {
            throw new IllegalArgumentException("Chat room type not supported");
        }

        User updateUser = userRepository.findById(userId)
                .orElseThrow(() -> userUtils.throwUserNotFoundException("ID", userId.toString()));

        ChatRoomMembership adminMember = chatRoomMembershipRepository
                .findByUserIdAndChatId(adminId, chatRoom.getId())
                .orElseThrow(() -> new EntityNotFoundException("Chat room membership not found"));

        if (!adminMember.getRole().equals(MemberRole.ADMIN)) {
            throw new IllegalArgumentException("Permission denied. Not allowed to change user role");
        }

        ChatRoomMembership chatRoomMembership = chatRoomMembershipRepository
                .findByUserIdAndChatId(updateUser.getId(), chatRoom.getId())
                .orElseThrow(() -> new EntityNotFoundException("Chat room membership not found"));

        chatRoomMembership.setRole(request.role());
        chatRoomMembershipRepository.save(chatRoomMembership);

        log.info("group role for user {} successfully changed", updateUser.getId());
        return userUtils.convertToUserDto(updateUser);
    }

    @Transactional
    public MessageResponseDto leaveGroup(Long chatId, UUID userId) {
        log.info("Leaving group for user {}", userId);

        ChatRoom chatRoom = findChatRoomById(chatId);

        if (!chatRoom.getType().equals(ChatRoomType.GROUP)) {
            throw new IllegalArgumentException("Chat room type not supported");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> userUtils.throwUserNotFoundException("ID", userId.toString()));

        ChatRoomMembership membership = chatRoomMembershipRepository
                .findByUserIdAndChatId(user.getId(), chatRoom.getId())
                .orElseThrow(() -> new EntityNotFoundException("Chat room membership not found"));

        chatRoomMembershipRepository.delete(membership);

        List<ChatRoomMembership> memberships =  chatRoomMembershipRepository.findByChatRoomId(chatRoom.getId());
        if (membership.getRole().equals(MemberRole.ADMIN)) {
            if (!memberships.isEmpty()) {
                ChatRoomMembership newAdminMember = memberships.get(0);
                newAdminMember.setRole(MemberRole.ADMIN);
                chatRoomMembershipRepository.save(newAdminMember);
            } else {
                chatRoomRepository.delete(chatRoom);
                log.info("Deleted empty chat room {}", chatRoom.getId());
                return new MessageResponseDto("You left the group. Group deleted since no members left.");
            }
        }

        String content = user.getFirstName() + " " + user.getLastName() + " has left the group";

        // Determine recipients
        List<User> recipients = memberships.stream()
                .map(ChatRoomMembership::getUser)
                .filter(usr -> !usr.getId().equals(user.getId()))
                .toList();

        processSendMessage(systemUser, chatRoom, content, chatRoom.getType(), recipients, false);

        return new MessageResponseDto("You have left the group successfully");
    }

    private ChatRoom findChatRoomById(Long chatRoomId) {
        return chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new EntityNotFoundException("Chat room not found"));
    }
}
