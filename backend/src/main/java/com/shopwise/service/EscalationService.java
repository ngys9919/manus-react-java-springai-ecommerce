package com.shopwise.service;

import com.shopwise.config.ShopwiseProperties;
import com.shopwise.domain.Conversation;
import com.shopwise.domain.EscalationTicket;
import com.shopwise.repository.EscalationTicketRepository;
import com.shopwise.security.ActorResolver.ActorContext;
import com.shopwise.web.dto.SupportDtos.EscalationView;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
public class EscalationService {

    private final EscalationTicketRepository ticketRepository;
    private final ConversationService conversationService;
    private final ShopwiseProperties properties;

    public EscalationService(EscalationTicketRepository ticketRepository,
                             ConversationService conversationService,
                             ShopwiseProperties properties) {
        this.ticketRepository = ticketRepository;
        this.conversationService = conversationService;
        this.properties = properties;
    }

    @Transactional
    public EscalationView escalate(ActorContext actor,
                                   Conversation conversation,
                                   String reason,
                                   String note,
                                   String email,
                                   boolean urgent) {

        String reasonCode = normalizeReason(reason);
        EscalationTicket.EscalationPriority priority = resolvePriority(reasonCode, urgent);
        String summary = buildSummary(conversation, reasonCode, note);
        String transcript = conversationService.transcriptExcerpt(conversation.getId(), 4000);

        EscalationTicket ticket = ticketRepository.save(EscalationTicket.builder()
                .reference(nextReference())
                .conversationId(conversation.getId())
                .userId(actor.userId())
                .contactEmail(email != null && !email.isBlank() ? email : actor.email())
                .reason(reasonCode)
                .priority(priority)
                .status(EscalationTicket.EscalationStatus.OPEN)
                .summary(summary)
                .transcriptExcerpt(transcript)
                .build());

        conversationService.markEscalated(conversation);
        return toView(ticket);
    }

    @Transactional(readOnly = true)
    public List<EscalationView> recent(int limit) {
        return ticketRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, Math.clamp(limit, 1, 50)))
                .stream()
                .map(this::toView)
                .toList();
    }

    @Transactional(readOnly = true)
    public EscalationView latestForConversation(Long conversationId) {
        return ticketRepository.findFirstByConversationIdOrderByIdDesc(conversationId)
                .map(this::toView)
                .orElse(null);
    }

    @Transactional
    public EscalationView resolve(String reference) {
        EscalationTicket ticket = ticketRepository.findByReferenceIgnoreCase(reference)
                .orElseThrow(() -> com.shopwise.exception.ApiException.notFound("Ticket " + reference));
        ticket.setStatus(EscalationTicket.EscalationStatus.RESOLVED);
        return toView(ticketRepository.save(ticket));
    }

    public EscalationView toView(EscalationTicket ticket) {
        return new EscalationView(
                ticket.getReference(),
                null,
                ticket.getReason(),
                reasonLabel(ticket.getReason()),
                ticket.getPriority().name(),
                ticket.getStatus().name(),
                ticket.getSummary(),
                ticket.getTranscriptExcerpt(),
                ticket.getContactEmail(),
                ticket.getCreatedAt(),
                expectedResponse(ticket.getPriority()),
                "Keep this reference. An agent can pick up the full transcript from where we stopped.");
    }

    private String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return "explicit_request";
        }
        String normalized = reason.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        return switch (normalized) {
            case "order_issue", "payment", "complaint", "safety", "policy",
                 "unresolved", "explicit_request" -> normalized;
            default -> "explicit_request";
        };
    }

    private EscalationTicket.EscalationPriority resolvePriority(String reason, boolean urgent) {
        if (urgent) {
            return EscalationTicket.EscalationPriority.URGENT;
        }
        return switch (reason) {
            case "safety" -> EscalationTicket.EscalationPriority.URGENT;
            case "order_issue", "payment", "complaint" -> EscalationTicket.EscalationPriority.HIGH;
            default -> EscalationTicket.EscalationPriority.NORMAL;
        };
    }

    private String expectedResponse(EscalationTicket.EscalationPriority priority) {
        return switch (priority) {
            case URGENT -> "An agent picks this up within 1 working hour.";
            case HIGH -> "An agent replies within 4 working hours.";
            case NORMAL -> "An agent replies within 1 working day.";
            case LOW -> "An agent replies within 2 working days.";
        };
    }

    private String reasonLabel(String reason) {
        return switch (reason) {
            case "order_issue" -> "Order problem";
            case "payment" -> "Payment question";
            case "complaint" -> "Complaint";
            case "safety" -> "Safety concern";
            case "policy" -> "Policy exception";
            case "unresolved" -> "Assistant could not resolve";
            default -> "Shopper asked for a person";
        };
    }

    private String buildSummary(Conversation conversation, String reasonCode, String note) {
        // This is the one-line headline in the staff queue, so it stays short and single-line.
        // The transcript travels in its own field and is shown on demand.
        StringBuilder builder = new StringBuilder(reasonLabel(reasonCode));
        if (note != null && !note.isBlank()) {
            builder.append(" — shopper reports: ").append(collapse(note));
        }
        builder.append(" (").append(conversation.getMessageCount()).append(" messages");
        if (conversation.getLastIntent() != null) {
            builder.append(", last intent ").append(conversation.getLastIntent());
        }
        builder.append(')');
        String summary = builder.toString();
        return summary.length() <= 200 ? summary : summary.substring(0, 197) + "…";
    }

    /** Flattens a free-text note onto one line so the queue stays scannable. */
    private String collapse(String text) {
        String flat = text.replaceAll("\\s+", " ").trim();
        return flat.length() <= 120 ? flat : flat.substring(0, 117) + "…";
    }

    private String nextReference() {
        long count = ticketRepository.count();
        return "SW-ESC-" + String.format(Locale.ROOT, "%04d", count + 1);
    }

    public String supportEmail() {
        return properties.getCommerce().getSupportEmail();
    }
}
