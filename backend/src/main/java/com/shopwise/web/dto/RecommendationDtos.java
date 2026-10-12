package com.shopwise.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Wire shapes for the recommendation API.
 *
 * <p>Every recommendation carries the reason it was made. A rail that cannot explain itself is
 * impossible to debug and impossible to trust: "customers who bought this also bought" and "popular
 * this week" are different claims, and the UI should be able to say which one it is making.</p>
 */
public final class RecommendationDtos {

    private RecommendationDtos() {
    }

    /** One recommended product, with the reason it is here and the strategy that produced it. */
    public record RecommendedProduct(
            ProductSummary product,
            double score,
            String reason,
            String strategy) {
    }

    /**
     * A rendered rail.
     *
     * @param recommendationId groups the impressions of this rail with the clicks that follow, which
     *                         is what makes click-through measurable per placement
     * @param fallback         true when personalisation was not possible and the rail fell back to a
     *                         non-personal strategy; the UI says so rather than pretending
     * @param note             plain-language explanation of a fallback, null when there is none
     */
    public record RecommendationRail(
            String strategy,
            String title,
            String subtitle,
            String anchor,
            String recommendationId,
            List<RecommendedProduct> items,
            boolean fallback,
            String note) {
    }

    /** A single behavioural or measurement event sent by the browser. */
    public record TrackedEvent(
            @NotNull String type,
            @NotNull Long productId,
            String recommendationId,
            String surface) {
    }

    public record EventBatch(
            @Valid @NotNull @Size(max = 60) List<TrackedEvent> events) {
    }

    /** @param rejected events the server refused, which are only ever measurement events it cannot trust */
    public record EventAck(int recorded, int rejected) {
    }

    /** Health of the model behind the rails, surfaced on the staff insights page. */
    public record RecommendationModelStatus(
            boolean enabled,
            int productsModelled,
            long productsWithBehaviour,
            long interactions,
            String builtAt) {
    }

    /** Click-through per placement, so a rail that nobody acts on can be retired. */
    public record PlacementPerformance(
            String surface,
            long impressions,
            long clicks,
            double clickThroughRate) {
    }
}
