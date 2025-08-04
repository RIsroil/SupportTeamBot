package com.example.supportbot.service;

import com.example.supportbot.dto.ChatMessagesResponseDTO;
import com.example.supportbot.dto.ChatResponseDto;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AdminService {
    void sendMessage(String chatId, String message, MultipartFile file);

    List<ChatResponseDto> getChats(int page, int size, String search);

    Optional<ChatMessagesResponseDTO> getMessages(UUID chatId);

    String markMessagesAsRead(UUID chatId);

    String closeChat(UUID chatId);
}

