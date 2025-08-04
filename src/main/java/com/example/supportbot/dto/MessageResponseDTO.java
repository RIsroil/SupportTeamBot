package com.example.supportbot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MessageResponseDTO {
    private UUID messageId;
    private String sender;
    private String message;
    private String time;
    private String file;
    private Boolean read;
}
