package com.example.supportbot.repository;

import com.example.supportbot.entity.ChatEntity;
import com.example.supportbot.entity.MessageEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MessageRepository extends JpaRepository<MessageEntity, UUID> {

    Optional<MessageEntity> findTopByChatOrderByCreatedAtDesc(ChatEntity chat);

    Optional<MessageEntity> findTopByChatAndSenderOrderByCreatedAtDesc(ChatEntity chat, String sender);

    @Query("SELECT m FROM MessageEntity m WHERE m.chat = :chat ORDER BY m.createdAt ASC")
    List<MessageEntity> findAllByChatOrderByCreatedAtAsc(@Param("chat") ChatEntity chat);

    List<MessageEntity> findAllByChat(ChatEntity chat);

    Page<MessageEntity> findAllByChatOrderByCreatedAtDesc(ChatEntity chat, Pageable pageable);

    @Query("SELECT m FROM MessageEntity m WHERE m.chat = :chat AND m.isRead = false AND m.sender = 'USER' ORDER BY m.createdAt ASC")
    List<MessageEntity> findUnreadUserMessages(@Param("chat") ChatEntity chat);

    @Modifying
    @Query("UPDATE MessageEntity m SET m.isRead = true WHERE m.chat = :chat AND m.sender = 'USER' AND m.isRead = false")
    int markAllUserMessagesAsRead(@Param("chat") ChatEntity chat);

    List<MessageEntity> findByChatAndSenderAndIsRead(ChatEntity chat, String sender, boolean isRead);

    @Query("SELECT COUNT(m) FROM MessageEntity m WHERE m.chat = :chat AND m.isRead = false AND m.sender = 'USER'")
    long countUnreadMessages(@Param("chat") ChatEntity chat);

    @Query("SELECT m FROM MessageEntity m WHERE m.sender = 'USER' ORDER BY m.createdAt DESC")
    Page<MessageEntity> findRecentUserMessages(Pageable pageable);

}
