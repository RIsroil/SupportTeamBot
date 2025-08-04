package com.example.supportbot.service;

import com.example.supportbot.bot.SupportTelegramBot;
import com.example.supportbot.dto.ChatMessagesResponseDTO;
import com.example.supportbot.dto.ChatResponseDto;
import com.example.supportbot.dto.MessageResponseDTO;
import com.example.supportbot.entity.ChatEntity;
import com.example.supportbot.entity.MessageEntity;
import com.example.supportbot.repository.ChatRepository;
import com.example.supportbot.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.File;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static java.util.Objects.isNull;

@Service
@Log4j2
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    private final ChatRepository chatRepository;
    private final MessageRepository messageRepository;
    private final SupportTelegramBot telegramBot;

    @Override
    public void sendMessage(String chatId, String message, MultipartFile file) {
        if (chatId == null || chatId.trim().isEmpty()) {
            ResponseEntity.badRequest().body("chatId is required");
        }

        if (isNull(message) && (file == null || file.isEmpty())) {
            ResponseEntity.badRequest().body("Either message or a valid file is required");
        }

        assert chatId != null;
        ChatEntity chat = chatRepository.findById(UUID.fromString(chatId))
                .orElseThrow(() -> new RuntimeException("Chat not found: " + chatId));

        try {
            String fileLink = null;
            if (message != null) {
                log.info("Sending text message: {} to telegramId: {}", message, chat.getUser().getTelegramId());
                SendMessage sendMessage = new SendMessage();
                sendMessage.setChatId(chat.getUser().getTelegramId().toString());
                sendMessage.setText(message);
                telegramBot.execute(sendMessage);
            }

            if (file != null && !file.isEmpty()) {
                log.info("Sending file: {} to telegramId: {}", file.getOriginalFilename(), chat.getUser().getTelegramId());
                SendPhoto sendPhoto = new SendPhoto();
                sendPhoto.setChatId(chat.getUser().getTelegramId().toString());
                sendPhoto.setPhoto(new InputFile(file.getInputStream(), file.getOriginalFilename()));
                Message sentMessage = telegramBot.execute(sendPhoto);

                String fileId = sentMessage.getPhoto().getFirst().getFileId();
                GetFile getFile = new GetFile();
                getFile.setFileId(fileId);
                File telegramFile = telegramBot.execute(getFile);
                fileLink = telegramFile.getFilePath() != null ?
                        "https://api.telegram.org/file/bot" + telegramBot.getBotToken() + "/" + telegramFile.getFilePath() :
                        file.getOriginalFilename();
                log.info("Generated file link: {}", fileLink);
            }

            MessageEntity messageEntity = new MessageEntity();
            messageEntity.setChat(chat);
            messageEntity.setSender("ADMIN");
            messageEntity.setMessage(message != null ? message : (file != null && !file.isEmpty() ? "Photo sent by admin" : null));
            messageEntity.setFile(fileLink);
            messageEntity.setCreatedAt(LocalDateTime.now());
            messageEntity.setRead(true);

            List<MessageEntity> userMessages = messageRepository.findByChatAndSenderAndIsRead(chat, "USER", false);
            for (MessageEntity messages : userMessages) {
                messages.setRead(true);
            }
            messageRepository.saveAll(userMessages);

            log.info("Saving message entity: {}", messageEntity);
            messageRepository.save(messageEntity);
            log.info("Message entity saved successfully for chatId: {}", chatId);

            chat.setUpdatedAt(LocalDateTime.now());
            chat.setLastMessage(message != null ? message : (fileLink != null ? fileLink : (file != null && !file.isEmpty() ? file.getOriginalFilename() : null)));
            chat.setLastMessageDate(LocalDateTime.now());
            chatRepository.save(chat);
            log.info("Chat updated successfully for chatId: {}", chatId);

        } catch (TelegramApiException | IOException e) {
            log.error("Error in sendMessage for chatId: {}", chatId, e);
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error sending message: " + e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error in sendMessage for chatId: {}", chatId, e);
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Unexpected error: " + e.getMessage());
        }
    }

    @Override
    public List<ChatResponseDto> getChats(int page, int size, String search) {
        Page<ChatEntity> chats;
        PageRequest pageRequest = PageRequest.of(page, size);

        if (search != null && !search.trim().isEmpty()) {
            chats = chatRepository.searchChats(search, pageRequest);
        } else {
            chats = chatRepository.findAllChatsOrdered(pageRequest);
        }

        return chats.stream().map(chat -> {
            MessageEntity lastMessage = messageRepository.findTopByChatOrderByCreatedAtDesc(chat)
                    .orElse(null);

            boolean hasFile = false;
            if (lastMessage != null) {
                if ("USER".equals(lastMessage.getSender()) && lastMessage.getFile() != null) {
                    hasFile = true;
                } else if ("SYSTEM".equals(lastMessage.getSender())) {
                    List<MessageEntity> messages = messageRepository.findAllByChatOrderByCreatedAtDesc(chat, PageRequest.of(0, 2)).getContent();
                    if (messages.size() > 1 && "USER".equals(messages.get(1).getSender()) && messages.get(1).getFile() != null) {
                        hasFile = true;
                    }
                }
            }

            return ChatResponseDto.builder()
                    .chatId(chat.getId())
                    .telegramId(chat.getUser().getTelegramId())
                    .username(chat.getUser().getUsername())
                    .firstName(chat.getUser().getFirstName())
                    .lastName(chat.getUser().getLastName())
                    .lastMessage(lastMessage != null ? lastMessage.getMessage() : null)
                    .lastMessageDate(lastMessage != null ? lastMessage.getCreatedAt() : null)
                    .isClosed(chat.isClosed())
                    .lastMessageSender(lastMessage != null ? lastMessage.getSender() : null)
                    .hasFile(hasFile)
                    .build();
        }).collect(Collectors.toList());
    }

    @Override
    public Optional<ChatMessagesResponseDTO> getMessages(UUID chatId) {
        log.debug("Fetching messages for chatId: {}", chatId);

        return chatRepository.findById(chatId)
                .map(chat -> {
                    List<MessageEntity> messages = messageRepository.findAllByChatOrderByCreatedAtAsc(chat);

                    List<MessageResponseDTO> messageList = Optional.ofNullable(messages)
                            .orElse(Collections.emptyList())
                            .stream()
                            .filter(msg -> msg != null && msg.getSender() != null &&
                                    ("USER".equals(msg.getSender()) || "SYSTEM".equals(msg.getSender()) || "ADMIN".equals(msg.getSender())))
                            .map(msg -> MessageResponseDTO.builder()
                                    .messageId(msg.getId())
                                    .sender(msg.getSender())
                                    .message(msg.getMessage())
                                    .time(msg.getCreatedAt() != null ? msg.getCreatedAt().toString() : null)
                                    .file(msg.getFile())
                                    .read(msg.isRead())
                                    .build())
                            .collect(Collectors.toList());

                    log.debug("Found {} messages for chatId: {}", messageList.size(), chatId);

                    return ChatMessagesResponseDTO.builder()
                            .telegramId(chat.getUser() != null ? chat.getUser().getTelegramId() : null)
                            .closed(chat.isClosed())
                            .username(chat.getUser() != null ? chat.getUser().getUsername() : null)
                            .firstName(chat.getUser() != null ? chat.getUser().getFirstName() : null)
                            .lastName(chat.getUser() != null ? chat.getUser().getLastName() : null)
                            .data(messageList)
                            .status("success")
                            .build();
                });
    }

    @Override
    public String markMessagesAsRead(UUID chatId) {
        Optional<ChatEntity> optionalChat = chatRepository.findById(chatId);
        if (optionalChat.isEmpty()) {
            return "Chat not found";
        }

        ChatEntity chat = optionalChat.get();
        if (chat.getUser() == null || chat.getUser().getTelegramId() == null) {
            log.error("Invalid user or telegramId for chatId: {}", chatId);
            return "Invalid chat user data";
        }

        List<MessageEntity> userMessages = messageRepository.findByChatAndSenderAndIsRead(chat, "USER", false);
        int updated = 0;

        for (MessageEntity message : userMessages) {
            message.setRead(true);
            updated++;
            log.info("Marked message {} as read for chatId: {}", message.getId(), chatId);
        }
        messageRepository.saveAll(userMessages);
        log.info("Marked {} user messages as read for chatId: {}", updated, chatId);

        String notificationMessage = """
                Sizning habaringiz adminlar tomonidan o'qildi, tez orada javob beramiz.
                Ваше сообщение прочитано администраторами, скоро ответим.
                Your message has been read by admins, we will respond soon.
                """;
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chat.getUser().getTelegramId().toString());
        sendMessage.setText(notificationMessage);
        try {
            telegramBot.execute(sendMessage);
        } catch (Exception e) {
            throw new RuntimeException("error on send telegram" + e.getMessage());
        }
        log.info("Notification sent successfully to telegramId: {}", chat.getUser().getTelegramId());

        return "Marked " + updated + " messages as read";
    }

    @Override
    @Transactional
    public String closeChat(UUID chatId) {
        Optional<ChatEntity> optionalChat = chatRepository.findById(chatId);
        if (optionalChat.isEmpty()) {
            return "Chat not found";
        }

        ChatEntity chat = optionalChat.get();
        if (chat.isClosed()) {
            return "Chat is already closed";
        }

        if (chat.getUser() == null || chat.getUser().getTelegramId() == null) {
            return "Invalid chat user data";
        }
        chat.setClosed(true);
        chat.setUpdatedAt(LocalDateTime.now());
        chatRepository.save(chat);
        log.info("Chat closed successfully for chatId: {}", chatId);

        String thankYouMsg = """
                Murojaatingiz uchun rahmat ✅
                Agar qo‘shimcha savolingiz bo‘lsa, /start tugmasini bosing.
                
                Thank you for contacting support ✅
                For further help, please click /start.
                
                Спасибо за ваше обращение ✅
                Для новых вопросов нажмите /start.
                """;
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chat.getUser().getTelegramId().toString());
        sendMessage.setText(thankYouMsg);
        try {
            telegramBot.execute(sendMessage);

        } catch (TelegramApiException e) {
            throw new RuntimeException("error on send telegram" + e.getMessage());
        }

        MessageEntity messageEntity = new MessageEntity();
        messageEntity.setChat(chat);
        messageEntity.setSender("SYSTEM");
        messageEntity.setMessage(thankYouMsg);
        messageEntity.setCreatedAt(LocalDateTime.now());
        messageEntity.setRead(true);

        List<MessageEntity> userMessages = messageRepository.findByChatAndSenderAndIsRead(chat, "USER", false);
        for (MessageEntity messages : userMessages) {
            messages.setRead(true);
        }

        messageRepository.saveAll(userMessages);
        log.info("Saving system message entity: {}", messageEntity);
        messageRepository.save(messageEntity);
        log.info("System message entity saved successfully for chatId: {}", chatId);

        chat.setLastMessage(thankYouMsg);
        chat.setLastMessageDate(LocalDateTime.now());
        chat.setUpdatedAt(LocalDateTime.now());
        chatRepository.save(chat);
        log.info("Chat last message updated for chatId: {}", chatId);

        return "Chat closed and notification sent to user";

    }
}