package com.example.supportbot.repository;

import com.example.supportbot.entity.ChatEntity;
import com.example.supportbot.entity.UserEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ChatRepository extends JpaRepository<ChatEntity, UUID> {

    Optional<ChatEntity> findTopByUserOrderByCreatedAtDesc(UserEntity user);

    Optional<ChatEntity> findTopByUserAndIsClosedFalseOrderByCreatedAtDesc(UserEntity user);

    @Query("SELECT c FROM ChatEntity c WHERE c.isClosed = false ORDER BY c.updatedAt DESC, c.createdAt DESC")
    Page<ChatEntity> findActiveChats(Pageable pageable);

    @Query("SELECT c FROM ChatEntity c ORDER BY c.updatedAt DESC, c.createdAt DESC")
    Page<ChatEntity> findAllChatsOrdered(Pageable pageable);

    @Query("SELECT c FROM ChatEntity c WHERE " +
            "(c.user.username LIKE %:search% OR " +
            "c.user.firstName LIKE %:search% OR " +
            "c.user.lastName LIKE %:search%) " +
            "ORDER BY c.updatedAt DESC, c.createdAt DESC")
    Page<ChatEntity> searchChats(@Param("search") String search, Pageable pageable);

    @Query("SELECT DISTINCT c FROM ChatEntity c " +
            "JOIN MessageEntity m ON m.chat = c " +
            "WHERE m.isRead = false AND m.sender = 'USER' " +
            "ORDER BY c.updatedAt DESC, c.createdAt DESC")
    Page<ChatEntity> findChatsWithUnreadMessages(Pageable pageable);

    @Query("SELECT COUNT(m) FROM MessageEntity m WHERE m.chat = :chat AND m.isRead = false AND m.sender = 'USER'")
    long countUnreadMessages(@Param("chat") ChatEntity chat);
}