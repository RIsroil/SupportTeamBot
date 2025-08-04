package com.example.supportbot.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class ChatResponseDto {
    private UUID chatId;
    private Long telegramId;
    private String username;
    private String firstName;
    private String lastName;
    private String lastMessage;
    private LocalDateTime lastMessageDate;
    private boolean isClosed;
    private String lastMessageSender;
    private boolean hasFile;
}
