package com.shopwise.web.dto;

import java.time.Instant;
import java.util.List;

/** Basic usage analytics for the assistant, aggregated over a rolling window. */
public record AnalyticsSummary(
        Instant windowStart,
        Instant windowEnd,
        int windowDays,
        long conversationsStarted,
        long activeConversations,
        long messagesSent,
        long messagesAnswered,
        long toolInvocations,
        long fallbacks,
        long moderationBlocks,
        long escalations,
        long escalationsOpen,
        long errors,
        long positiveFeedback,
        long negativeFeedback,
        double resolutionRate,
        double fallbackRate,
        double escalationRate,
        double moderationBlockRate,
        double errorRate,
        Double averageLatencyMs,
        double satisfactionScore,
        List<CountItem> topIntents,
        List<CountItem> topTools,
        List<CountItem> moderationCategories,
        List<DailyPoint> dailyMessages,
        List<DailyPoint> dailyFallbacks,
        List<EscalationEntry> recentEscalations,
        List<ModerationEntry> recentModeration) {

    public record CountItem(String key, String label, long count) {
    }

    public record DailyPoint(String day, long count) {
    }

    public record EscalationEntry(
            String reference,
            String reason,
            String priority,
            String status,
            String summary,
            Instant createdAt) {
    }

    public record ModerationEntry(
            String direction,
            String category,
            String severity,
            String action,
            String excerpt,
            Instant createdAt) {
    }
}