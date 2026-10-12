package com.shopwise.ai;

import com.shopwise.config.ShopwiseProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Thin, defensive wrapper around the Spring AI {@link ChatClient}.
 *
 * <p>The assistant must never hang or fail hard in front of a shopper, so this class
 * adds three things: a configuration check, a bounded call with timing, and a small
 * circuit breaker that trips after repeated provider failures and recovers by itself.</p>
 */
@Component
public class AssistantRuntime {

    private static final Logger log = LoggerFactory.getLogger(AssistantRuntime.class);
    private static final int FAILURE_THRESHOLD = 3;
    private static final Duration OPEN_DURATION = Duration.ofMinutes(5);

    public record LlmResult(
            boolean ok,
            /**
             * True when the call itself succeeded but the model produced no prose.
             * The caller keeps any tool results instead of discarding real work.
             */
            boolean empty,
            String text,
            String model,
            long latencyMs,
            String failureReason) {

        public static LlmResult failed(String reason, String model, long latencyMs) {
            return new LlmResult(false, false, null, model, latencyMs, reason);
        }

        public static LlmResult empty(String model, long latencyMs) {
            return new LlmResult(false, true, null, model, latencyMs,
                    "The model returned no commentary for this turn.");
        }

        public static LlmResult success(String text, String model, long latencyMs) {
            return new LlmResult(true, false, text, model, latencyMs, null);
        }
    }

    private final ChatClient chatClient;
    private final ShopwiseProperties properties;
    private final String configuredApiKey;

    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private final AtomicReference<Instant> circuitOpenedAt = new AtomicReference<>();

    public AssistantRuntime(ChatClient shoppingAssistantChatClient,
                            ShopwiseProperties properties,
                            @Value("${spring.ai.openai.api-key:}") String configuredApiKey) {
        this.chatClient = shoppingAssistantChatClient;
        this.properties = properties;
        this.configuredApiKey = configuredApiKey;
    }

    public boolean isConfigured() {
        return properties.getAssistant().isEnabled()
                && configuredApiKey != null
                && !configuredApiKey.isBlank()
                && !"not-configured".equalsIgnoreCase(configuredApiKey.trim());
    }

    /** @return null when the assistant can call the model, otherwise a human-readable reason. */
    public String unavailableReason() {
        if (!properties.getAssistant().isEnabled()) {
            return "The AI assistant is switched off in this deployment.";
        }
        if (!isConfigured()) {
            return "No model credentials are configured for this deployment.";
        }
        Instant openedAt = circuitOpenedAt.get();
        if (openedAt != null) {
            if (Duration.between(openedAt, Instant.now()).compareTo(OPEN_DURATION) < 0) {
                return "The model is temporarily unavailable after repeated errors.";
            }
            circuitOpenedAt.compareAndSet(openedAt, null);
            consecutiveFailures.set(0);
        }
        return null;
    }

    public String modelName() {
        return properties.getAssistant().getModel();
    }

    /**
     * One assistant turn. Never throws: any provider problem is returned as a failed
     * result so the caller can fall back deterministically.
     */
    public LlmResult converse(String systemPrompt,
                              List<Message> history,
                              String userMessage,
                              Object tools,
                              Map<String, Object> toolContext) {

        String unavailable = unavailableReason();
        if (unavailable != null) {
            return LlmResult.failed(unavailable, modelName(), 0);
        }

        long started = System.currentTimeMillis();
        try {
            ChatClient.ChatClientRequestSpec spec = chatClient.prompt()
                    .system(systemPrompt)
                    .messages(history)
                    .user(userMessage);

            if (tools != null) {
                spec = spec.tools(tools);
            }
            if (toolContext != null && !toolContext.isEmpty()) {
                spec = spec.toolContext(toolContext);
            }

            String content = spec.call().content();
            long latency = System.currentTimeMillis() - started;

            if (content == null || content.isBlank()) {
                // The provider answered, so this is not a transport failure and must not
                // trip the breaker; it just means there is no prose to show.
                return LlmResult.empty(modelName(), latency);
            }
            consecutiveFailures.set(0);
            circuitOpenedAt.set(null);
            return LlmResult.success(content.trim(), modelName(), latency);

        } catch (Exception ex) {
            long latency = System.currentTimeMillis() - started;
            registerFailure();
            log.warn("Assistant model call failed after {} ms: {}", latency, ex.toString());
            return LlmResult.failed(classify(ex), modelName(), latency);
        }
    }

    private void registerFailure() {
        if (consecutiveFailures.incrementAndGet() >= FAILURE_THRESHOLD) {
            circuitOpenedAt.compareAndSet(null, Instant.now());
            log.warn("Assistant circuit breaker opened after {} consecutive failures", FAILURE_THRESHOLD);
        }
    }

    private String classify(Exception ex) {
        String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
        if (message.contains("401") || message.toLowerCase().contains("unauthor")) {
            return "The model rejected our credentials.";
        }
        if (message.contains("429") || message.toLowerCase().contains("rate limit")) {
            return "The model is rate limiting us right now.";
        }
        if (message.contains("timed out") || message.contains("timeout")) {
            return "The model took too long to answer.";
        }
        if (message.contains("Connection") || message.contains("connect")) {
            return "We could not reach the model service.";
        }
        return "The model call failed unexpectedly.";
    }

    /** Support hook so staff can see whether the breaker is open. */
    public boolean circuitOpen() {
        Instant openedAt = circuitOpenedAt.get();
        return openedAt != null && Duration.between(openedAt, Instant.now()).compareTo(OPEN_DURATION) < 0;
    }
}
