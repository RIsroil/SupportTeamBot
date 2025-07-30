package com.example.supportbot.controller;

import com.example.supportbot.bot.SupportTelegramBot;
import com.example.supportbot.dto.AdminMessageRequestDto;
import com.example.supportbot.dto.ChatResponseDto;
import com.example.supportbot.entity.ChatEntity;
import com.example.supportbot.entity.MessageEntity;
import com.example.supportbot.repository.ChatRepository;
import com.example.supportbot.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.File;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static java.util.Objects.isNull;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final ChatRepository chatRepository;
    private final MessageRepository messageRepository;
    private final SupportTelegramBot telegramBot; // Autowired qo'shildi

    @PostMapping("/{chatId}")
    public ResponseEntity<String> sendMessage(
            @PathVariable("chatId") String chatId,
            @RequestParam(value = "message", required = false) String message,
            @RequestParam(value = "file", required = false) MultipartFile file) {
        if (chatId == null || chatId.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("chatId majburiy");
        }

        if (isNull(message) && isNull(file)) {
            return ResponseEntity.badRequest().body("message yoki file dan kamida biri kerak");
        }

        ChatEntity chat = chatRepository.findById(UUID.fromString(chatId))
                .orElseThrow(() -> new RuntimeException("Chat topilmadi: " + chatId));

        try {
            if (message != null) {
                SendMessage sendMessage = new SendMessage();
                sendMessage.setChatId(chat.getUser().getTelegramId().toString());
                sendMessage.setText(message);
                telegramBot.execute(sendMessage);
            }

            String fileLink = null;
            if (file != null) {
                // Rasmni Telegramga yuklash
                SendPhoto sendPhoto = new SendPhoto();
                sendPhoto.setChatId(chat.getUser().getTelegramId().toString());
                sendPhoto.setPhoto(new InputFile(file.getInputStream(), file.getOriginalFilename()));
                Message sentMessage = telegramBot.execute(sendPhoto);

                // fileId ni olish
                String fileId = sentMessage.getPhoto().get(0).getFileId();

                // File linkini olish
                GetFile getFile = new GetFile();
                getFile.setFileId(fileId);
                File telegramFile = telegramBot.execute(getFile);
                fileLink = "https://api.telegram.org/file/bot" + telegramBot.getBotToken() + "/" + telegramFile.getFilePath();
            }

            // Database ga saqlash
            MessageEntity messageEntity = new MessageEntity();
            messageEntity.setChat(chat);
            messageEntity.setSender("ADMIN");
            messageEntity.setMessage(message != null ? message : null);
            messageEntity.setFile(fileLink != null ? fileLink : (file != null ? file.getOriginalFilename() : null));
            messageEntity.setCreatedAt(LocalDateTime.now());
            messageEntity.setRead(false);
            messageRepository.save(messageEntity);

            chat.setUpdatedAt(LocalDateTime.now());
            chat.setLastMessage(message != null ? message : (fileLink != null ? fileLink : (file != null ? file.getOriginalFilename() : null)));
            chat.setLastMessageDate(LocalDateTime.now());
            chatRepository.save(chat);

            return ResponseEntity.ok("Xabar muvaffaqiyatli yuborildi");
        } catch (TelegramApiException | java.io.IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Xato: " + e.getMessage());
        }
    }

    @GetMapping("/chats")
    public ResponseEntity<List<ChatResponseDto>> getChats(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search) {
        Page<ChatEntity> chats;
        if (search != null && !search.isEmpty()) {
            chats = chatRepository.findAll(PageRequest.of(page, size)); // Search logikasi qo'shish uchun kengaytirish mumkin
        } else {
            chats = chatRepository.findAll(PageRequest.of(page, size));
        }

        List<ChatResponseDto> response = chats.stream().map(chat -> {
            MessageEntity lastMessage = messageRepository.findTopByChatOrderByCreatedAtDesc(chat)
                    .orElse(null);
            return ChatResponseDto.builder()
                    .chatId(chat.getId())
                    .telegramId(chat.getUser().getTelegramId())
                    .username(chat.getUser().getUsername())
                    .firstName(chat.getUser().getFirstName())
                    .lastName(chat.getUser().getLastName())
                    .lastMessage(lastMessage != null ? lastMessage.getMessage() : null)
                    .lastMessageDate(lastMessage != null ? lastMessage.getCreatedAt() : null)
                    .isClosed(chat.isClosed())
                    .lastMessageSender(lastMessage != null ? lastMessage.getSender() : null) // Kim yozgan
                    .hasFile(lastMessage != null && lastMessage.getFile() != null) // File mavjudligi
                    .build();
        }).collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{chatId}")
    public ResponseEntity<List<MessageEntity>> getMessages(@PathVariable UUID chatId) {
        return chatRepository.findById(chatId)
                .map(chat -> ResponseEntity.ok(messageRepository.findAllByChat(chat)))
                .orElseGet(() -> ResponseEntity.badRequest().build());
    }

    @PostMapping("/{chatId}/mark-read")
    public ResponseEntity<String> markMessagesAsRead(@PathVariable UUID chatId) {
        Optional<ChatEntity> optionalChat = chatRepository.findById(chatId);
        if (optionalChat.isEmpty()) {
            return ResponseEntity.badRequest().body("Chat topilmadi");
        }

        List<MessageEntity> messages = messageRepository.findAllByChat(optionalChat.get());
        messages.forEach(message -> {
            if (!message.isRead()) {
                message.setRead(true);
                messageRepository.save(message);
            }
        });

        return ResponseEntity.ok("Xabarlar o‘qilgan deb belgilandi");
    }
}