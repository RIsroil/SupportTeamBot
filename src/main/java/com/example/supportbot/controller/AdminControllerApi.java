package com.example.supportbot.controller;

import com.example.supportbot.dto.ChatMessagesResponseDTO;
import com.example.supportbot.dto.ChatResponseDto;
import jakarta.transaction.Transactional;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RequestMapping("/api/admin")
public interface AdminControllerApi {

    @PostMapping(value = "/{chatId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional(rollbackOn = Exception.class)
    void sendMessage(
            @PathVariable("chatId") String chatId,
            @RequestParam(value = "message", required = false) String message,
            @RequestParam(value = "file", required = false) MultipartFile file);

    @GetMapping("/chats")
    List<ChatResponseDto> getChats(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search);

    @GetMapping("/{chatId}")
    Optional<ChatMessagesResponseDTO> getMessages(@PathVariable UUID chatId);

    @PostMapping("/{chatId}/mark-read")
    String markMessagesAsRead(@PathVariable UUID chatId);

    @PostMapping("/{chatId}/close")
    String closeChat(@PathVariable UUID chatId);
}
