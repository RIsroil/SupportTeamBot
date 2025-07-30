package com.example.supportbot.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class AdminMessageRequestDto {
    private UUID chatId;      // ChatEntity id
    private String message;   // Admin yuboradigan xabar
}
