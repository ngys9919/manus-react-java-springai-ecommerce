package com.shopwise.service;

import com.shopwise.domain.AssistantEvent;
import com.shopwise.domain.Conversation;
import com.shopwise.domain.EscalationTicket;
import com.shopwise.domain.ModerationLog;
import com.shopwise.repository.AssistantEventRepository;
import com.shopwise.repository.ConversationRepository;
import com.shopwise.repository.EscalationTicketRepository;
import com.shopwise.repository.ModerationLogRepository;
import com.shopwise.web.dto.AnalyticsSummary;
import com.shopwise.web.dto.AnalyticsSummary.CountItem;
import com.shopwise.web.dto.AnalyticsSummary.DailyPoint;
import com.shopwise.web.dto.AnalyticsSummary.EscalationEntry;
import com.shopwise.web.dto.AnalyticsSummary.ModerationEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AnalyticsService {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsService.class);

    private final AssistantEventRepository eventRepository;
    private final ConversationRepository conversationRepository;
    private final EscalationTicketRepository escalationRepository;
    private final ModerationLogRepository moderationLogRepository;
    private final AuditWriter auditWriter;

    public AnalyticsService(AssistantEventRepository eventRepository,
                            ConversationRepository conversationRepository,
                            EscalationTicketRepository escalationRepository,
                            ModerationLogRepository moderationLogRepository,
                            AuditWriter auditWriter) {
        this.eventRepository = eventRepository;
        this.conversationRepository = conversationRepository;
        this.escalationRepository = escalationRepository;
        this.moderationLogRepository = moderationLogRepository;
        this.auditWriter = auditWriter;
    }

    /** Records one analytics event. Never fails the caller. */
    public void record(Long conversationId, Long userId, String eventType, String intent,
                       String toolName, String outcome, Double value, Map<String, Object> metadata) {
        try {
            auditWriter.writeEvent(AssistantEvent.builder()
                    .conversationId(conversationId)
                    .userId(userId)
                    .eventType(eventType)
                    .intent(intent)
                    .toolName(toolName)
                    .outcome(outcome)
                    .valueNum(value == null ? null : BigDecimal.valueOf(value))
                    .metadata(metadata)
                    .build());
        } catch (RuntimeException ex) {
            log.warn("Could not record assistant event {}: {}", eventType, ex.getMessage());
        }
    }

    public void record(Long conversationId, Long userId, String eventType) {
        record(conversationId, userId, eventType, null, null, null, null, null);
    }

    @Transactional(readOnly = true)
    public AnalyticsSummary summary(int windowDays) {
        int days = Math.clamp(windowDays, 1, 90);
        Instant to = Instant.now();
        Instant from = to.minus(days, ChronoUnit.DAYS);

        Map<String, Long> byType = new LinkedHashMap<>();
        for (Object[] row : eventRepository.countByEventTypeSince(from)) {
            byType.put(String.valueOf(row[0]), ((Number) row[1]).longValue());
        }

        long conversationsStarted = byType.getOrDefault(AssistantEvent.Type.CONVERSATION_STARTED, 0L);
        long messagesSent = byType.getOrDefault(AssistantEvent.Type.MESSAGE_SENT, 0L);
        long messagesAnswered = byType.getOrDefault(AssistantEvent.Type.MESSAGE_ANSWERED, 0L);
        long toolInvocations = byType.getOrDefault(AssistantEvent.Type.TOOL_CALLED, 0L);
        long fallbacks = byType.getOrDefault(AssistantEvent.Type.FALLBACK_TRIGGERED, 0L);
        long moderationBlocks = byType.getOrDefault(AssistantEvent.Type.MODERATION_BLOCKED, 0L);
        long escalations = byType.getOrDefault(AssistantEvent.Type.ESCALATION_REQUESTED, 0L);
        long errors = byType.getOrDefault(AssistantEvent.Type.ASSISTANT_ERROR, 0L);
        long rateLimited = byType.getOrDefault(AssistantEvent.Type.RATE_LIMITED, 0L);
        long positive = byType.getOrDefault(AssistantEvent.Type.FEEDBACK_RECEIVED + "_UP", 0L);
        long negative = byType.getOrDefault(AssistantEvent.Type.FEEDBACK_RECEIVED + "_DOWN", 0L);

        long activeConversations = conversationRepository.countByStatus(Conversation.ConversationStatus.ACTIVE);
        long escalationsOpen = escalationRepository.countByStatus(EscalationTicket.EscalationStatus.OPEN);

        Double averageLatency = eventRepository.averageLatencySince(from);

        // Quality rates are shares of *answered* turns, not of inbound turns. A turn that was
        // refused by moderation, held for a human or rate limited is answered without ever being
        // counted as a sent message, so dividing by messagesSent produced rates above 100%.
        // Fallbacks are answers too, so the answered total is the larger of the two counters.
        long answered = Math.max(messagesAnswered, fallbacks);
        long resolved = Math.max(0L, answered - fallbacks);

        double resolutionRate = rate(resolved, answered);
        double fallbackRate = rate(fallbacks, answered);
        double escalationRate = rate(escalations, conversationsStarted);
        double moderationRate = rate(moderationBlocks, messagesSent);
        double errorRate = rate(errors, answered);
        double satisfaction = rate(positive, positive + negative);

        return new AnalyticsSummary(
                from,
                to,
                days,
                conversationsStarted,
                activeConversations,
                messagesSent,
                answered,
                toolInvocations,
                fallbacks,
                moderationBlocks,
                escalations,
                escalationsOpen,
                errors,
                positive,
                negative,
                resolutionRate,
                fallbackRate,
                escalationRate,
                moderationRate,
                errorRate,
                averageLatency,
                satisfaction,
                countItems(eventRepository.topIntentsSince(from, PageRequest.of(0, 8))),
                countItems(eventRepository.topToolsSince(from, PageRequest.of(0, 8))),
                countItems(moderationLogRepository.countByCategorySince(from)),
                dailyPoints(eventRepository.dailyMessageVolumeSince(from)),
                dailyPoints(eventRepository.dailyFallbackVolumeSince(from)),
                escalationRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, 8)).stream()
                        .map(ticket -> new EscalationEntry(
                                ticket.getReference(),
                                ticket.getReason(),
                                ticket.getPriority().name(),
                                ticket.getStatus().name(),
                                ticket.getSummary(),
                                ticket.getCreatedAt()))
                        .toList(),
                moderationLogRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, 10)).stream()
                        .map(entry -> new ModerationEntry(
                                entry.getDirection(),
                                entry.getCategory(),
                                entry.getSeverity(),
                                entry.getAction(),
                                entry.getExcerpt(),
                                entry.getCreatedAt()))
                        .toList());
    }

    public long rateLimitedCount() {
        return eventRepository.countByEventTypeAndCreatedAtAfter(
                AssistantEvent.Type.RATE_LIMITED, Instant.now().minus(1, ChronoUnit.DAYS));
    }

    private List<CountItem> countItems(List<Object[]> rows) {
        return rows.stream()
                .map(row -> new CountItem(String.valueOf(row[0]), humanise(String.valueOf(row[0])),
                        ((Number) row[1]).longValue()))
                .toList();
    }

    private List<DailyPoint> dailyPoints(List<Object[]> rows) {
        return rows.stream()
                .map(row -> new DailyPoint(String.valueOf(row[0]), ((Number) row[1]).longValue()))
                .toList();
    }

    private String humanise(String raw) {
        if (raw == null || raw.isBlank() || "null".equals(raw)) {
            return "Other";
        }
        String spaced = raw.replace('_', ' ').trim();
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }

    private double round(double value) {
        return Math.round(value * 10d) / 10d;
    }

    /**
     * A percentage that can never leave 0–100. Counters come from independent event types, so a
     * partial window can make the numerator exceed the denominator; clamping keeps a dashboard
     * from reporting an impossible rate instead of silently lying.
     */
    private double rate(long numerator, long denominator) {
        if (denominator <= 0L) {
            return 0d;
        }
        return round(Math.clamp(100d * numerator / denominator, 0d, 100d));
    }


    public static ModerationLog.Direction directionOf(String raw) {
        return "INBOUND".equalsIgnoreCase(raw) ? ModerationLog.Direction.INBOUND : ModerationLog.Direction.OUTBOUND;
    }
}
