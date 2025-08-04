package com.example.supportbot.bot;

import com.example.supportbot.config.TelegramBotConfig;
import com.example.supportbot.entity.ChatEntity;
import com.example.supportbot.entity.MessageEntity;
import com.example.supportbot.entity.UserEntity;
import com.example.supportbot.repository.ChatRepository;
import com.example.supportbot.repository.MessageRepository;
import com.example.supportbot.repository.UserRepository;
import lombok.extern.log4j.Log4j2;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.*;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;


@Component
@Log4j2
public class SupportTelegramBot extends TelegramLongPollingBot {

    private final TelegramBotConfig config;
    private final UserRepository userRepository;
    private final ChatRepository chatRepository;
    private final MessageRepository messageRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public SupportTelegramBot(TelegramBotConfig config, UserRepository userRepository,
                              ChatRepository chatRepository, MessageRepository messageRepository,
                              SimpMessagingTemplate messagingTemplate) {
        this.config = config;
        this.userRepository = userRepository;
        this.chatRepository = chatRepository;
        this.messageRepository = messageRepository;
        this.messagingTemplate = messagingTemplate;

        List<BotCommand> commands = new ArrayList<>();
        commands.add(new BotCommand("/start", "Botni ishga tushirish"));
        try {
            execute(new SetMyCommands(commands, new BotCommandScopeDefault(), null));
            log.info("✅ Bot buyruqlari muvaffaqiyatli o'rnatildi");
        } catch (TelegramApiException e) {
            log.error("❌ Bot buyruqlarini o'rnatishda xato: {}", e.getMessage(), e);
        }
    }

    @Override
    public String getBotUsername() {
        return config.getUsername();
    }

    public String getGroupId() {
        return config.getGroupId();
    }

    @Override
    public String getBotToken() {
        return config.getToken();
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (!update.hasMessage()) {
            return;
        }

        Message message = update.getMessage();
        Long userChatId = message.getChatId();

        try {
            UserEntity user = getOrCreateUser(message);
            ChatEntity chat = getOrReopenChat(user);

            if (message.hasPhoto()) {
                handlePhotoMessage(message, chat, userChatId);
            } else if (message.hasText()) {
                handleTextMessage(message, chat, userChatId);
            }
        } catch (Exception e) {
            log.error("❌ Xabar qayta ishlashda xato, user {}: {}", userChatId, e.getMessage(), e);
            sendReply(userChatId, "⚠️ Xatolik yuz berdi. Iltimos, qayta urinib ko'ring.");
        }
    }

    private UserEntity getOrCreateUser(Message message) {
        Long userChatId = message.getChatId();
        UserEntity user = userRepository.findByTelegramId(userChatId)
                .orElseGet(() -> UserEntity.builder().telegramId(userChatId).build());
        user.setUsername(message.getFrom().getUserName());
        user.setFirstName(message.getFrom().getFirstName());
        user.setLastName(message.getFrom().getLastName());
        return userRepository.save(user);
    }

    private ChatEntity getOrReopenChat(UserEntity user) {
        Optional<ChatEntity> lastChat = chatRepository.findTopByUserOrderByCreatedAtDesc(user);
        if (lastChat.isPresent()) {
            ChatEntity chat = lastChat.get();
            if (chat.isClosed()) {
                chat.setClosed(false);
                chat.setUpdatedAt(LocalDateTime.now());
                log.info("🔓 Chat qayta ochildi: {} for user: {}", chat.getId(), user.getTelegramId());
            }
            return chatRepository.save(chat);
        }
        ChatEntity newChat = ChatEntity.builder()
                .user(user)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .isClosed(false)
                .build();
        log.info("🆕 Yangi chat yaratildi: {} for user: {}", newChat.getId(), user.getTelegramId());
        return chatRepository.save(newChat);
    }

    private void handlePhotoMessage(Message message, ChatEntity chat, Long userChatId) {
        try {
            List<PhotoSize> photos = message.getPhoto();
            String fileId = photos.get(photos.size() - 1).getFileId();

            String groupChatId = getGroupId();
            if (groupChatId != null && !groupChatId.trim().isEmpty()) {
                SendPhoto sendPhoto = new SendPhoto();
                sendPhoto.setChatId(groupChatId);
                sendPhoto.setPhoto(new InputFile(fileId));
                execute(sendPhoto);
            }

            String downloadUrl = getFileDownloadUrl(fileId);

            String caption = message.getCaption();
            String messageContent = caption != null ? caption : "📷 Rasm";

            MessageEntity messageEntity = createMessage(chat, "USER", messageContent, downloadUrl);
            updateChatLastMessage(chat, messageContent, messageEntity.getCreatedAt());

            sendAutomatedResponse(chat, userChatId);
            messagingTemplate.convertAndSend("/topic/chats", messageEntity);

            log.info("✅ Rasm xabari qayta ishlandi, chat: {}", chat.getId());
        } catch (TelegramApiException e) {
            log.error("❌ Rasm yuborishda xato, chatId: {}", userChatId, e);
            sendReply(userChatId, "⚠️ Rasm yuborishda xato yuz berdi: " + e.getMessage());
        }
    }

    private void handleTextMessage(Message message, ChatEntity chat, Long userChatId) {
        String text = message.getText().trim().toLowerCase();
        boolean isStartCommand = text.equals("/start") || text.equals("start");

        // Xabarni saqlash
        MessageEntity messageEntity = createMessage(chat, "USER", text, null);
        updateChatLastMessage(chat, text, messageEntity.getCreatedAt());
        messagingTemplate.convertAndSend("/topic/chats", messageEntity);

        if (isStartCommand) {
            handleStartCommand(chat, userChatId);
        } else if (shouldSendAutomatedResponseForChat(chat)) {
            sendAutomatedResponse(chat, userChatId);
        }

        log.info("✅ Matn xabari qayta ishlandi, chat: {}", chat.getId());
    }

    private void handleStartCommand(ChatEntity chat, Long userChatId) {
        String welcomeMessage = """
                🌟 Xush kelibsiz! / Welcome! / Добро пожаловать!
                
                🇺🇿 O'zbekcha:
                Assalomu alaykum! 👋
                Siz Parapay qo'llab-quvvatlash xizmatiga murojaat qildingiz.
                Savollaringiz bo'lsa, bemalol yozing!
                
                🇷🇺 Русский:
                Здравствуйте! 👋
                Вы обратились в поддержку Parapay.
                Напишите ваш вопрос, мы обязательно ответим!
                
                🇺🇸 English:
                Hello! 👋
                You have contacted Parapay support.
                If you have any questions, feel free to write!
                """;

        sendReply(userChatId, welcomeMessage);
        MessageEntity welcomeEntity = createMessage(chat, "SYSTEM", welcomeMessage, null);
        updateChatLastMessage(chat, "🎉 Suhbat boshlandi", welcomeEntity.getCreatedAt());
        messagingTemplate.convertAndSend("/topic/chats", welcomeEntity);

        log.info("🚀 /start buyrug'i qayta ishlandi, chat: {}", chat.getId());
    }

    private void sendAutomatedResponse(ChatEntity chat, Long userChatId) {
        String responseText = """
                ✅ So'rovingiz qabul qilindi!
                📞 Tez orada operator javob beradi.
                
                ✅ Your request has been received!
                📞 An operator will respond soon.
                
                ✅ Ваш запрос получен!
                📞 Оператор ответит в ближайшее время.
                """;

        sendReply(userChatId, responseText);
        MessageEntity responseEntity = createMessage(chat, "SYSTEM", responseText, null);
        updateChatLastMessage(chat, responseText, responseEntity.getCreatedAt());
        messagingTemplate.convertAndSend("/topic/chats", responseEntity);
    }

    private MessageEntity createMessage(ChatEntity chat, String sender, String message, String file) {
        MessageEntity messageEntity = MessageEntity.builder()
                .chat(chat)
                .sender(sender)
                .message(message)
                .file(file)
                .createdAt(LocalDateTime.now())
                .isRead("SYSTEM".equals(sender))
                .build();
        return messageRepository.save(messageEntity);
    }

    private void updateChatLastMessage(ChatEntity chat, String message, LocalDateTime timestamp) {
        String truncatedMessage = message != null && message.length() > 255
                ? message.substring(0, 252) + "..."
                : message;
        chat.setLastMessage(truncatedMessage);
        chat.setLastMessageDate(timestamp);
        chat.setUpdatedAt(LocalDateTime.now());
        chatRepository.save(chat);
    }

    private String getFileDownloadUrl(String fileId) throws TelegramApiException {
        GetFile getFile = new GetFile();
        getFile.setFileId(fileId);
        File file = execute(getFile);
        return "https://api.telegram.org/file/bot" + getBotToken() + "/" + file.getFilePath();
    }

    private boolean shouldSendAutomatedResponseForChat(ChatEntity chat) {
        List<MessageEntity> messages = messageRepository.findAllByChat(chat);
        if (messages.isEmpty()) {
            return true;
        }

        Optional<MessageEntity> lastAdminMessage = messages.stream()
                .filter(msg -> "ADMIN".equals(msg.getSender()))
                .max((m1, m2) -> m1.getCreatedAt().compareTo(m2.getCreatedAt()));

        if (lastAdminMessage.isEmpty()) {
            return messages.stream().filter(msg -> "USER".equals(msg.getSender())).count() <= 1;
        }

        MessageEntity lastAdminMsg = lastAdminMessage.get();
        long minutesSinceLastAdminMessage = ChronoUnit.MINUTES.between(lastAdminMsg.getCreatedAt(), LocalDateTime.now());
        if (minutesSinceLastAdminMessage >= 45) {
            return !messages.stream()
                    .anyMatch(msg -> "USER".equals(msg.getSender()) &&
                            msg.getCreatedAt().isAfter(lastAdminMsg.getCreatedAt()));
        }
        return false;
    }

    public void sendReply(Long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(text);
        message.setParseMode("HTML");
        executeSafe(message);
    }

    private void executeSafe(SendMessage msg) {
        try {
            execute(msg);
            log.debug("✅ Xabar muvaffaqiyatli yuborildi, chat: {}", msg.getChatId());
        } catch (TelegramApiException e) {
            log.error("❌ Xabar yuborishda xato, chatId: {}, xato: {}", msg.getChatId(), e.getMessage(), e);
        }
    }
}