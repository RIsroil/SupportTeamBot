package com.example.supportbot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageResponseDto {
    private UUID id;
    private UUID chatId;
    private String sender;
    private String message;
    private String file;
    private LocalDateTime createdAt;
    private boolean isRead;
}