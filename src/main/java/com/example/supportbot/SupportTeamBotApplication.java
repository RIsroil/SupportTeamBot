package com.example.supportbot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;

@SpringBootApplication
@EnableWebSocketMessageBroker
public class SupportTeamBotApplication {

    public static void main(String[] args) {
        SpringApplication.run(SupportTeamBotApplication.class, args);
    }

}