package com.shopwise.web.dto;

import java.util.List;
import java.util.UUID;

public record ChatResponse(
        UUID conversationId,
        String title,
        String status,
        MessageView reply,
        List<QuickReply> quickReplies,
        String intent,
        Double confidence,
        /** True when the answer came from the deterministic engine instead of the model. */
        boolean degraded,
        /** Human readable reason for a degraded answer, surfaced in the UI. */
        String degradedReason,
        String model,
        long latencyMs,
        int remainingMessagesThisMinute,
        boolean escalated) {
}