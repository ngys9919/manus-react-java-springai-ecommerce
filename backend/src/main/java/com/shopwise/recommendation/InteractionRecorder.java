package com.shopwise.recommendation;

import com.shopwise.domain.ProductInteraction;
import com.shopwise.domain.ProductInteraction.InteractionType;
import com.shopwise.repository.ProductInteractionRepository;
import com.shopwise.repository.ProductRepository;
import com.shopwise.security.ActorResolver.ActorContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Appends to the interaction log.
 *
 * <p>Two design decisions worth stating.</p>
 *
 * <p><b>The server records behaviour; the browser records attention.</b> Views, cart adds and
 * purchases are written here, from the services that already handle them, because the server is the
 * only party that knows they actually happened. Impressions and clicks come from the browser,
 * because only the browser knows what was on screen. Letting the client report a purchase would
 * mean trusting it to describe revenue.</p>
 *
 * <p><b>Writes are isolated and best-effort.</b> Recording an interaction is never allowed to fail a
 * shopper's request, so each write runs in its own transaction and problems are logged rather than
 * propagated. A recommendation system that can break checkout is worse than no recommendation
 * system.</p>
 */
@Component
public class InteractionRecorder {

    private static final Logger log = LoggerFactory.getLogger(InteractionRecorder.class);

    private final ProductInteractionRepository interactionRepository;
    private final ProductRepository productRepository;
    private final RecommendationModel model;

    public InteractionRecorder(ProductInteractionRepository interactionRepository,
                               ProductRepository productRepository,
                               RecommendationModel model) {
        this.interactionRepository = interactionRepository;
        this.productRepository = productRepository;
        this.model = model;
    }

    /** {@code user:<id>} for a signed-in shopper, {@code guest:<token>} otherwise. */
    public static String actorKey(ActorContext actor) {
        if (actor == null) {
            return "anonymous";
        }
        if (actor.userId() != null) {
            return "user:" + actor.userId();
        }
        if (actor.guestToken() != null && !actor.guestToken().isBlank()) {
            return "guest:" + actor.guestToken();
        }
        return "anonymous";
    }

    public void view(ActorContext actor, Long productId) {
        recordQuietly(actorKey(actor), actor == null ? null : actor.userId(),
                productId, InteractionType.VIEW, "catalog", null);
    }

    public void cartAdd(ActorContext actor, Long productId) {
        recordQuietly(actorKey(actor), actor == null ? null : actor.userId(),
                productId, InteractionType.CART_ADD, "cart", null);
    }

    public void purchase(ActorContext actor, List<Long> productIds) {
        String key = actorKey(actor);
        Long userId = actor == null ? null : actor.userId();
        for (Long productId : productIds) {
            recordQuietly(key, userId, productId, InteractionType.PURCHASE, "checkout", null);
        }
    }

    /**
     * Records a batch of browser-reported events.
     *
     * <p>Only measurement events are accepted. A client claiming a purchase or a cart add is
     * rejected, so a crafted request cannot inflate the behavioural signal that drives everyone
     * else's recommendations.</p>
     *
     * @return how many were stored and how many were refused
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Result recordClientEvents(ActorContext actor, List<ClientEvent> events) {
        String key = actorKey(actor);
        Long userId = actor == null ? null : actor.userId();

        List<ProductInteraction> accepted = new ArrayList<>();
        int rejected = 0;

        for (ClientEvent event : events) {
            InteractionType type = parse(event.type());
            if (type == null || type.isPreferenceSignal()) {
                rejected++;
                continue;
            }
            if (event.productId() == null || !productRepository.existsById(event.productId())) {
                rejected++;
                continue;
            }
            accepted.add(ProductInteraction.builder()
                    .product(productRepository.getReferenceById(event.productId()))
                    .userId(userId)
                    .actorKey(key)
                    .interaction(type)
                    .weight(type.weightValue())
                    .recommendationId(truncate(event.recommendationId(), 64))
                    .surface(truncate(event.surface(), 40))
                    .createdAt(Instant.now())
                    .build());
        }

        if (!accepted.isEmpty()) {
            interactionRepository.saveAll(accepted);
        }
        return new Result(accepted.size(), rejected);
    }

    public record ClientEvent(String type, Long productId, String recommendationId, String surface) {
    }

    public record Result(int recorded, int rejected) {
    }

    private void recordQuietly(String actorKey, Long userId, Long productId,
                               InteractionType type, String surface, String recommendationId) {
        if (productId == null) {
            return;
        }
        try {
            write(actorKey, userId, productId, type, surface, recommendationId);
        } catch (RuntimeException ex) {
            // Deliberately swallowed: telemetry must never break the request that produced it.
            log.warn("Could not record {} for product {}: {}", type, productId, ex.toString());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void write(String actorKey, Long userId, Long productId,
                      InteractionType type, String surface, String recommendationId) {
        if (!productRepository.existsById(productId)) {
            return;
        }
        interactionRepository.saveAndFlush(ProductInteraction.builder()
                .product(productRepository.getReferenceById(productId))
                .userId(userId)
                .actorKey(actorKey)
                .interaction(type)
                .weight(type.weightValue())
                .recommendationId(truncate(recommendationId, 64))
                .surface(truncate(surface, 40))
                .createdAt(Instant.now())
                .build());
    }

    private static InteractionType parse(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return InteractionType.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
