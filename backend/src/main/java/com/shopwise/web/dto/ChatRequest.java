package com.shopwise.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * A single shopper turn. {@code pageContext} lets the storefront tell the assistant
 * what the shopper is looking at ("product:SKU-1001", "cart", "checkout") so replies
 * stay grounded in the current screen.
 */
public record ChatRequest(
        @NotBlank(message = "message is required")
        @Size(max = 1200, message = "message must be 1200 characters or fewer")
        String message,

        UUID conversationId,

        @Size(max = 64, message = "pageContext is too long")
        String pageContext) {
}