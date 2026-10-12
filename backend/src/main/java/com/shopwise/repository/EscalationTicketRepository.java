package com.shopwise.repository;

import com.shopwise.domain.EscalationTicket;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EscalationTicketRepository extends JpaRepository<EscalationTicket, Long> {

    Optional<EscalationTicket> findByReferenceIgnoreCase(String reference);

    Optional<EscalationTicket> findFirstByConversationIdOrderByIdDesc(Long conversationId);

    List<EscalationTicket> findAllByOrderByCreatedAtDesc(Pageable pageable);

    long countByStatus(EscalationTicket.EscalationStatus status);
}
