package com.shopwise.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class SupportDtos {

    private SupportDtos() {
    }

    public record EscalationRequest(
            @Size(max = 64, message = "reason is too long")
            String reason,

            @Size(max = 400, message = "note is too long")
            String note,

            @Email(message = "email must be a valid address")
            String email,

            Boolean urgent) {
    }

    public record EscalationView(
            String reference,
            String conversationId,
            String reason,
            String reasonLabel,
            String priority,
            String status,
            String summary,

            /**
             * The conversation up to the handoff, so an agent never has to ask the shopper to
             * repeat themselves. Kept separate from {@code summary} so the staff queue can show a
             * one-line headline while the transcript stays available on demand.
             */
            String transcriptExcerpt,

            String contactEmail,
            Instant createdAt,
            String expectedResponse,
            String nextStep) {
    }

    public record FeedbackRequest(
            @NotBlank(message = "rating is required")
            String rating,

            @Size(max = 400, message = "comment is too long")
            String comment,

            Long messageId) {
    }

    public record FeedbackResponse(String recorded, String acknowledgement) {
    }
}
