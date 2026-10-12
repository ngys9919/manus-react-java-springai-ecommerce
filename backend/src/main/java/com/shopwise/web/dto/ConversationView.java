package com.shopwise.web.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ConversationView(
        UUID id,
        String title,
        String status,
        String lastIntent,
        int messageCount,
        Instant createdAt,
        Instant lastMessageAt,
        List<MessageView> messages) {
}