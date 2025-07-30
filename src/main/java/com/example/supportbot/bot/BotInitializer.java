package com.example.supportbot.bot;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class BotInitializer {

    private final SupportTelegramBot supportTelegramBot;

    @PostConstruct
    public void init() {
        try {
            log.info("Bot registratsiya boshlandi: {}", supportTelegramBot.getBotUsername());
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(supportTelegramBot);
            log.info("Bot muvaffaqiyatli registratsiya qilindi: @{}", supportTelegramBot.getBotUsername());
        } catch (TelegramApiException e) {
            log.error("Bot registratsiyasida xato: {}", e.getMessage(), e);
            throw new RuntimeException("Bot registratsiyasi muvaffaqiyatsiz: " + e.getMessage(), e);
        }
    }
}
