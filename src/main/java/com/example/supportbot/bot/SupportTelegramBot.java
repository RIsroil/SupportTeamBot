package com.example.supportbot.bot;

import com.example.supportbot.config.TelegramBotConfig;
import com.example.supportbot.entity.ChatEntity;
import com.example.supportbot.entity.MessageEntity;
import com.example.supportbot.entity.UserEntity;
import com.example.supportbot.repository.ChatRepository;
import com.example.supportbot.repository.MessageRepository;
import com.example.supportbot.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
public class SupportTelegramBot extends TelegramLongPollingBot {

    private static final Logger logger = LoggerFactory.getLogger(SupportTelegramBot.class);
    private final TelegramBotConfig config;
    private final UserRepository userRepository;
    private final ChatRepository chatRepository;
    private final MessageRepository messageRepository;
    private final SimpMessagingTemplate messagingTemplate;

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


    public SupportTelegramBot(TelegramBotConfig config, UserRepository userRepository,
                              ChatRepository chatRepository, MessageRepository messageRepository,
                              SimpMessagingTemplate messagingTemplate) {
        this.config = config;
        this.userRepository = userRepository;
        this.chatRepository = chatRepository;
        this.messageRepository = messageRepository;
        this.messagingTemplate = messagingTemplate;

        List<BotCommand> commands = new ArrayList<>();
        commands.add(new BotCommand("/start", "Start the bot"));

        try {
            this.execute(new SetMyCommands(commands, new BotCommandScopeDefault(), null));
            logger.info("Bot buyruqlari muvaffaqiyatli o‘rnatildi");
        } catch (TelegramApiException e) {
            logger.error("Bot buyruqlarini o‘rnatishda xato", e);
        }
    }

    @Override
    public void onUpdateReceived(Update update) {

        // Get Group Id
//        if (update.hasMessage()) {
//            Message message = update.getMessage();
//            Long chatId = message.getChatId();
//            logger.info("Guruh chatId: {}", chatId);
//        }
        if (update.hasMessage()) {
            Message message = update.getMessage();
            Long userChatId = message.getChatId();

            // Save or update user
            UserEntity user = userRepository.findByTelegramId(userChatId)
                    .orElseGet(() -> UserEntity.builder().telegramId(userChatId).build());
            user.setUsername(message.getFrom().getUserName());
            user.setFirstName(message.getFrom().getFirstName());
            user.setLastName(message.getFrom().getLastName());
            userRepository.save(user);

            ChatEntity chat = chatRepository.findTopByUserAndIsClosedFalseOrderByCreatedAtDesc(user)
                    .orElseGet(() -> {
                        ChatEntity newChat = ChatEntity.builder()
                                .user(user)
                                .createdAt(LocalDateTime.now())
                                .isClosed(false)
                                .build();
                        return chatRepository.save(newChat);
                    });

            if (message.hasPhoto()) {
                List<PhotoSize> photos = message.getPhoto();
                String fileId = photos.get(photos.size() - 1).getFileId();

                String groupChatId = getGroupId();
                SendPhoto sendPhoto = new SendPhoto();
                sendPhoto.setChatId(groupChatId);
                sendPhoto.setPhoto(new InputFile(fileId));
                try {
                    Message sentMessage = execute(sendPhoto);
                    String sentFileId = sentMessage.getPhoto().get(0).getFileId();

                    GetFile getFile = new GetFile();
                    getFile.setFileId(sentFileId);
                    File file = execute(getFile);
                    String filePath = file.getFilePath();
                    String downloadUrl = "https://api.telegram.org/file/bot" + getBotToken() + "/" + filePath;

                    String caption = message.getCaption();
                    String text = message.getText();
                    String messageContent = caption != null ? caption : (text != null ? text : null);

                    MessageEntity messageEntity = MessageEntity.builder()
                            .chat(chat)
                            .sender("USER")
                            .message(messageContent) // Matn yoki caption
                            .file(downloadUrl) // Linkni saqlash
                            .createdAt(LocalDateTime.now())
                            .build();
                    messageRepository.save(messageEntity);

                    sendReply(userChatId, "So'rovingiz qabul qilindi, tez orada javob beramiz.\nYour request has been received, we will respond soon.\nВаш запрос получен, мы ответим вам в ближайшее время.");
                } catch (TelegramApiException e) {
                    logger.error("Rasm yuborishda xato, chatId: {}", userChatId, e);
                    sendReply(userChatId, "Rasm yuborishda xato yuz berdi: " + e.getMessage());
                }
            } else if (message.hasText()) {
                String text = message.getText().toLowerCase();
                switch (text) {
                    case "/start":
                    case "start":
                        if (chat.isClosed()) {
                            chat = ChatEntity.builder()
                                    .user(user)
                                    .createdAt(LocalDateTime.now())
                                    .isClosed(false)
                                    .build();
                            chat = chatRepository.save(chat);
                        }
                        sendWelcomeMessage(userChatId);
                        return;
                    default:
                        boolean shouldSendAutomatedResponse = shouldSendAutomatedResponseForChat(chat);

                        MessageEntity savedMessage = MessageEntity.builder()
                                .chat(chat)
                                .sender("USER")
                                .message(text)
                                .createdAt(LocalDateTime.now())
                                .build();
                        messageRepository.save(savedMessage);

                        messagingTemplate.convertAndSend("/topic/chats", savedMessage);
                }
            }
        }
    }

    private boolean shouldSendAutomatedResponseForChat(ChatEntity chat) {
        List<MessageEntity> allMessages = messageRepository.findAllByChat(chat);

        if (allMessages.isEmpty()) {
            return true;
        }

        Optional<MessageEntity> lastAdminMessage = allMessages.stream()
                .filter(msg -> "ADMIN".equals(msg.getSender()))
                .max((m1, m2) -> m1.getCreatedAt().compareTo(m2.getCreatedAt()));

        if (lastAdminMessage.isEmpty()) {
            long userMessageCount = allMessages.stream()
                    .filter(msg -> "USER".equals(msg.getSender()))
                    .count();
            return userMessageCount <= 1;
        }

        MessageEntity lastAdminMsg = lastAdminMessage.get();
        LocalDateTime now = LocalDateTime.now();
        long minutesSinceLastAdminMessage = ChronoUnit.MINUTES.between(lastAdminMsg.getCreatedAt(), now);

        if (minutesSinceLastAdminMessage >= 45) {
            boolean hasUserMessageAfterAdmin = allMessages.stream()
                    .anyMatch(msg -> "USER".equals(msg.getSender()) &&
                            msg.getCreatedAt().isAfter(lastAdminMsg.getCreatedAt()));
            return !hasUserMessageAfterAdmin;
        }

        return false;
    }

    private void sendWelcomeMessage(Long chatId) {
        String welcomeMessage = """
                O'zbekcha:
                Assalomu alaykum! 👋
                Siz Parapay qo'llab-quvvatlash xizmatiga murojaat qildingiz.
                Savollaringiz bo'lsa, bemalol yozing!

                Русский:
                Здравствуйте! 👋
                Вы обратились в поддержку Parapay.
                Напишите ваш вопрос, мы обязательно ответим!
                
                English:
                Hello! 👋
                You have contacted Parapay support.
                If you have any questions, feel free to write!
                """;
        sendReply(chatId, welcomeMessage);
    }

    private void sendReply(Long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(text);
        executeSafe(message);
    }

    private void executeSafe(SendMessage msg) {
        try {
            execute(msg);
        } catch (TelegramApiException e) {
            logger.error("Xabar yuborishda xato, chatId: {}", msg.getChatId(), e);
        }
    }
}