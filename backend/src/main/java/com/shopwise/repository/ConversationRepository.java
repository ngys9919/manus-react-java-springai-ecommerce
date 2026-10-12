package com.shopwise.repository;

import com.shopwise.domain.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    Optional<Conversation> findByPublicId(UUID publicId);

    List<Conversation> findTop20ByUserIdOrderByLastMessageAtDesc(Long userId);

    List<Conversation> findTop20ByGuestTokenOrderByLastMessageAtDesc(String guestToken);

    long countByStatus(Conversation.ConversationStatus status);
}
