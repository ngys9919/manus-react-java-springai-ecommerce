package com.shopwise.ai;

import com.shopwise.config.ShopwiseProperties;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sliding-window rate limiting per conversation, per signed-in user and per guest
 * token, so a single shopper cannot burn the model budget or hammer the endpoint.
 */
@Service
public class RateLimitService {

    public record Decision(boolean allowed, int remaining, long retryAfterSeconds, String reason) {
    }

    private final Map<String, Deque<Instant>> windows = new ConcurrentHashMap<>();
    private final ShopwiseProperties properties;

    public RateLimitService(ShopwiseProperties properties) {
        this.properties = properties;
    }

    public Decision checkConversation(String conversationKey) {
        return check("conv:" + conversationKey,
                properties.getAssistant().getMaxMessagesPerMinute(),
                Duration.ofMinutes(1));
    }

    public Decision checkUser(Long userId) {
        if (userId == null) {
            return new Decision(true, Integer.MAX_VALUE, 0, null);
        }
        return check("user:" + userId,
                properties.getAssistant().getMaxMessagesPerHourPerUser(),
                Duration.ofHours(1));
    }

    public Decision checkAnonymous(String guestToken) {
        if (guestToken == null) {
            return new Decision(true, Integer.MAX_VALUE, 0, null);
        }
        return check("guest:" + guestToken, 40, Duration.ofHours(1));
    }

    private Decision check(String key, int limit, Duration window) {
        Instant now = Instant.now();
        Deque<Instant> hits = windows.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (hits) {
            Instant cutoff = now.minus(window);
            while (!hits.isEmpty() && hits.peekFirst().isBefore(cutoff)) {
                hits.pollFirst();
            }
            if (hits.size() >= limit) {
                Instant oldest = hits.peekFirst();
                long retryAfter = oldest == null
                        ? window.toSeconds()
                        : Math.max(1, Duration.between(now, oldest.plus(window)).toSeconds());
                return new Decision(false, 0, retryAfter,
                        "You have sent a lot of messages in a short time. Give me " + retryAfter
                                + " seconds and I will pick up where we left off.");
            }
            hits.addLast(now);
            return new Decision(true, Math.max(0, limit - hits.size()), 0, null);
        }
    }

    /** Test and support hook: forget all windows. */
    public void reset() {
        windows.clear();
    }
}