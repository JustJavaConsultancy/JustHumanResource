package com.justjava.humanresource.communication.repository;

import com.justjava.humanresource.communication.entity.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findByConversation_IdOrderByCreatedAtAsc(Long conversationId);

    Optional<ChatMessage> findFirstByConversation_IdOrderByCreatedAtDesc(Long conversationId);

    @Query("""
            SELECT m
            FROM ChatMessage m
            WHERE m.conversation.id = :conversationId
            ORDER BY m.createdAt DESC
            """)
    List<ChatMessage> findRecentByConversation(@Param("conversationId") Long conversationId, Pageable pageable);

    long countByRecipient_IdAndSender_IdAndReadAtIsNull(Long recipientId, Long senderId);
}
