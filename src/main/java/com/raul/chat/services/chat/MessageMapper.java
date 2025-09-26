package com.raul.chat.services.chat;

import com.raul.chat.dtos.chat.MessageDto;
import com.raul.chat.dtos.chat.RecipientDto;
import com.raul.chat.models.chat.Message;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MessageMapper {

    public MessageDto toMessageDto(Message message) {
        List<RecipientDto> recipientDtos = message.getRecipients().stream()
                .map(r -> new RecipientDto(
                        r.getRecipient().getId(),
                        r.getStatus(),
                        r.getDeliveredAt(),
                        r.getReadAt()
                ))
                .toList();

        return new MessageDto(
                message.getId(),
                message.getContent(),
                message.getSender().getId(),
                message.getChatRoom().getId(),
                recipientDtos,
                message.getSentAt()
        );
    }
}
