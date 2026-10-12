package com.shopwise.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Everything the app needs to be tuned without a rebuild.
 */
@ConfigurationProperties(prefix = "shopwise")
@Getter
@Setter
public class ShopwiseProperties {

    private Security security = new Security();
    private Assistant assistant = new Assistant();
    private Commerce commerce = new Commerce();

    @Getter
    @Setter
    public static class Security {
        /** HMAC signing key for the demo JWT issuer. Override in production. */
        private String jwtSecret = "shopwise-development-signing-key-change-me-please-32bytes";
        /** Token lifetime in minutes. */
        private long tokenTtlMinutes = 720;
        private String issuer = "shopwise";
        private List<String> allowedOrigins = new ArrayList<>(List.of(
                "http://localhost:5173",
                "http://127.0.0.1:5173",
                "http://localhost:4173"));

        /**
         * Accept a request whose Origin is the same host the browser is already talking to.
         *
         * <p>The Docker deployment serves the storefront and the API from one origin, with nginx
         * proxying {@code /api}. Browsers still attach an Origin header to every mutating request,
         * so without this the storefront could browse but never sign in or check out - and the
         * failure would only appear at the deployed hostname, never in the dev server, whose origin
         * is in the list above. Set to {@code false} to require an explicit allowlist.</p>
         */
        private boolean allowSameOrigin = true;
    }

    @Getter
    @Setter
    public static class Assistant {
        /** Master switch: when false every reply comes from the deterministic fallback engine. */
        private boolean enabled = true;
        /** Chat model id served by the configured OpenAI-compatible endpoint. */
        private String model = "gpt-5-mini";
        private Double temperature = 0.2;
        private Integer maxCompletionTokens = 1400;
        /** How many previous messages are replayed to the model for follow-up context. */
        private int memoryWindow = 16;
        /** Hard cap on user turns in a single conversation before the bot asks to escalate. */
        private int maxTurnsPerConversation = 40;
        /** Sliding-window rate limit per conversation. */
        private int maxMessagesPerMinute = 12;
        /** Sliding-window rate limit per authenticated user. */
        private int maxMessagesPerHourPerUser = 120;
        /** Reject oversized input before it reaches the model. */
        private int maxMessageLength = 1200;
        /** Products returned to the model per catalog search. */
        private int toolResultLimit = 8;
        /** Maximum number of products accepted by a single comparison. */
        private int maxComparisonSize = 4;
        /** Minutes before an unanswered escalated conversation may be resumed by the bot. */
        private int escalationCooldownMinutes = 30;
    }

    @Getter
    @Setter
    public static class Commerce {
        private BigDecimal freeShippingThreshold = new BigDecimal("150.00");
        private BigDecimal flatShippingFee = new BigDecimal("12.00");
        private BigDecimal taxRate = new BigDecimal("0.08");
        private String supportEmail = "support@shopwise.dev";
        private int lowStockThreshold = 5;
    }
}
