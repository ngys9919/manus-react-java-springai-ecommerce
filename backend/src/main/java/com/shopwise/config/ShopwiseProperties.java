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
    private Recommendations recommendations = new Recommendations();

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
         * Origin allow-list entries that may contain wildcards, matched by Spring's
         * {@code setAllowedOriginPatterns}.
         *
         * <p>This exists because the same-origin rule cannot be evaluated behind a proxy that
         * rewrites the {@code Host} header: the browser's Origin names the public hostname while
         * the backend only sees the proxy's internal target, so the two can never be compared.
         * A deployment reachable at a hostname it cannot otherwise learn - a generated sandbox
         * hostname, an ephemeral preview URL - declares it here instead:</p>
         *
         * <pre>SHOPWISE_ALLOWED_ORIGIN_PATTERNS=https://*.example.com,http://localhost:[*]</pre>
         *
         * <p>Prefer an exact {@code allowedOrigins} entry where the hostname is known. Note that
         * these patterns are matched only against the {@code Origin} header, and authorization is
         * still enforced by the token filter, so a permitted origin gains no access it could not
         * already obtain by replaying a request without a browser.</p>
         */
        private List<String> allowedOriginPatterns = new ArrayList<>(List.of(
                "http://localhost:[*]",
                "http://127.0.0.1:[*]"));

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

    @Getter
    @Setter
    public static class Recommendations {
        /** Master switch. When false the endpoints return empty rails rather than failing. */
        private boolean enabled = true;
        /** How far back the similarity model looks. Older behaviour says less about today. */
        private int similarityWindowDays = 90;
        /**
         * Half-life of the trending decay, in days. At 7, an interaction a week old counts half as
         * much as one from today.
         */
        private double trendingHalfLifeDays = 7.0;
        /** How long a built model is reused before being rebuilt from the interaction log. */
        private int modelTtlSeconds = 300;
        /** Rails default to this many items when the caller does not say. */
        private int defaultLimit = 8;
        private int maxLimit = 24;
        /**
         * How hard to push back on near-duplicates when filling a rail. 0 fills purely by score and
         * tends to return four variants of the same product; 0.4 optimises for variety.
         */
        private double diversityLambda = 0.25;
        /**
         * Diversity for rails that should stay on topic. "Similar to this" is expected to return
         * comparable products; spreading it across categories would be actively unhelpful, whereas
         * a home-page rail benefits from breadth.
         */
        private double focusedDiversityLambda = 0.08;
        /**
         * Weakest similarity a product may have and still appear in a "similar" rail. Without a
         * floor, the diversity penalty can push an unrelated product above a genuine match.
         */
        private double similarityFloor = 0.20;
        /**
         * Interactions needed before a product's behaviour is trusted as much as its attributes.
         * Below this the model leans on content similarity instead.
         */
        private int supportSaturation = 12;
        /** Minimum items in a rail before the response is marked as a fallback. */
        private int minimumUsefulRailSize = 4;
    }
}
