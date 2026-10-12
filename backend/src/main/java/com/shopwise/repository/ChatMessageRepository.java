package com.shopwise.repository;

import com.shopwise.domain.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findByConversationIdOrderByIdAsc(Long conversationId);

    List<ChatMessage> findTop20ByConversationIdOrderByIdDesc(Long conversationId);

    long countByConversationId(Long conversationId);
}
