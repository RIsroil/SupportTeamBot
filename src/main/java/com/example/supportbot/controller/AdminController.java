package com.example.supportbot.controller;

import com.example.supportbot.dto.ChatMessagesResponseDTO;
import com.example.supportbot.dto.ChatResponseDto;
import com.example.supportbot.service.AdminService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@Log4j2
@RequiredArgsConstructor
public class AdminController implements AdminControllerApi {

    private final AdminService adminService;

    public void sendMessage(String chatId, String message, MultipartFile file) {
        adminService.sendMessage(chatId, message, file);
    }

    public List<ChatResponseDto> getChats(int page, int size, String search) {
        return adminService.getChats(page, size, search);
    }

    public Optional<ChatMessagesResponseDTO> getMessages(UUID chatId) {
        return adminService.getMessages(chatId);
    }

    public String markMessagesAsRead(UUID chatId) {
        return adminService.markMessagesAsRead(chatId);
    }

    public String closeChat(UUID chatId) {
        return adminService.closeChat(chatId);
    }
}