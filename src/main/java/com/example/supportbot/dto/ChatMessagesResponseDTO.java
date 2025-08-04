package com.example.supportbot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ChatMessagesResponseDTO {
    private Long telegramId;
    private Boolean closed;
    private String username;
    private String firstName;
    private String lastName;
    private List<MessageResponseDTO> data;
    private String status;
}

