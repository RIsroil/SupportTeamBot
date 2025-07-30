package com.example.supportbot.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AdminNotificationDto {
    private final String chatId;
    private final Long telegramId;
    private final String firstName;
    private final String lastName;
    private final String username;
    private final String message;
    private final LocalDateTime timestamp;
    private final String language;

    public AdminNotificationDto(java.util.UUID chatId, Long telegramId, String firstName,
                                String lastName, String username, String message,
                                LocalDateTime timestamp, String language) {
        this.chatId = chatId.toString();
        this.telegramId = telegramId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.username = username;
        this.message = message;
        this.timestamp = timestamp;
        this.language = language;
    }
}