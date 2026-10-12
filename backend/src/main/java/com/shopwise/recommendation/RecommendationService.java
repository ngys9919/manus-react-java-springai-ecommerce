package com.shopwise.recommendation;

import com.shopwise.config.ShopwiseProperties;
import com.shopwise.domain.Product;
import com.shopwise.domain.ProductInteraction.InteractionType;
import com.shopwise.recommendation.RecommendationDataLoader.ProductFeatures;
import com.shopwise.recommendation.RecommendationModel.Model;
import com.shopwise.repository.ProductInteractionRepository;
import com.shopwise.repository.ProductRepository;
import com.shopwise.security.ActorResolver.ActorContext;
import com.shopwise.web.dto.RecommendationDtos.PlacementPerformance;
import com.shopwise.web.dto.RecommendationDtos.RecommendationModelStatus;
import com.shopwise.web.dto.RecommendationDtos.RecommendationRail;
import com.shopwise.web.dto.RecommendationDtos.RecommendedProduct;
import com.shopwise.web.mapper.ProductMapper;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * Builds recommendation rails.
 *
 * <p>Each public method answers one question a shopper actually has, and each degrades to a weaker
 * but honest answer rather than returning nothing:</p>
 *
 * <ul>
 *   <li>{@link #similar} - "what else is like this?" Behaviour where proven, attributes otherwise.</li>
 *   <li>{@link #alsoViewed} - "what did people look at alongside this?" Browsing co-occurrence.</li>
 *   <li>{@link #alsoBought} - "what do people actually buy with this?" Purchase co-occurrence.</li>
 *   <li>{@link #forActor} - "what should I look at next?" Personalised, with a stated fallback.</li>
 *   <li>{@link #trending} - "what is popular right now?" Time-decayed, and the cold-start answer.</li>
 *   <li>{@link #cartCrossSell} - "what am I missing from this order?"</li>
 * </ul>
 *
 * <p>Ranking is always followed by a diversity pass. Pure top-k on a catalogue with four products
 * per category reliably returns four near-identical items, which is the difference between a rail
 * that helps and one that looks broken.</p>
 */
@Service
public class RecommendationService {

    /** How fast personalisation forgets. Two weeks halves the weight of a browsing session. */
    private static final double HISTORY_HALF_LIFE_DAYS = 14.0;

    /** How much a rail discounts items the shopper has already looked at. */
    private static final double VIEWED_PENALTY = 0.35;

    /** Similarity below which a product is not worth padding a cross-sell rail with. */
    private static final double CROSS_SELL_SIMILARITY_FLOOR = 0.35;

    /** How much a different-category product is discounted in a "similar" rail. */
    private static final double CROSS_CATEGORY_DISCOUNT = 0.55;

    private final RecommendationModel modelHolder;
    private final ProductInteractionRepository interactionRepository;
    private final ProductRepository productRepository;
    private final ProductMapper productMapper;
    private final ShopwiseProperties properties;

    public RecommendationService(RecommendationModel modelHolder,
                                 ProductInteractionRepository interactionRepository,
                                 ProductRepository productRepository,
                                 ProductMapper productMapper,
                                 ShopwiseProperties properties) {
        this.modelHolder = modelHolder;
        this.interactionRepository = interactionRepository;
        this.productRepository = productRepository;
        this.productMapper = productMapper;
        this.properties = properties;
    }

    // ------------------------------------------------------------------
    // Product-anchored rails
    // ------------------------------------------------------------------

    /** Products similar to the given one, by behaviour where proven and by attributes otherwise. */
    @Transactional(readOnly = true)
    public RecommendationRail similar(Long productId, Integer requestedLimit) {
        int limit = clampLimit(requestedLimit);
        Model model = modelHolder.model();
        ProductFeatures anchor = model.features(productId);
        if (anchor == null) {
            return emptyRail("similar", "Similar products", null);
        }

        Map<Long, Double> scores = new LinkedHashMap<>();
        for (RecommendationModel.Scored neighbour : model.neighbours(productId, limit * 6)) {
            ProductFeatures candidate = model.features(neighbour.productId());
            if (candidate == null) {
                continue;
            }
            // A "similar" rail is expected to offer comparable alternatives, so a product from
            // another category is discounted rather than banned: it can still surface when the
            // behavioural evidence is much stronger, but it cannot crowd out the obvious answers.
            double score = candidate.categorySlug().equals(anchor.categorySlug())
                    ? neighbour.score()
                    : neighbour.score() * CROSS_CATEGORY_DISCOUNT;
            scores.put(neighbour.productId(), score);
        }

        // Drop weak matches before ranking, so the rail does not reach into another category
        // purely to fill a slot.
        double floor = properties.getRecommendations().getSimilarityFloor();
        scores.entrySet().removeIf(entry -> entry.getValue() < floor);

        String reason = "Similar to " + anchor.name();
        return rail("similar", "Similar products", null, anchor.name(), scores, limit,
                ignored -> reason, Set.of(productId), focusedDiversity());
    }

    /** Browsing co-occurrence: what shoppers looked at in the same session as this product. */
    @Transactional(readOnly = true)
    public RecommendationRail alsoViewed(Long productId, Integer requestedLimit) {
        int limit = clampLimit(requestedLimit);
        Model model = modelHolder.model();
        ProductFeatures anchor = model.features(productId);
        if (anchor == null) {
            return emptyRail("also_viewed", "Customers also viewed", null);
        }

        Map<Long, Double> scores = new LinkedHashMap<>();
        for (ProductFeatures candidate : model.products()) {
            if (candidate.id().equals(productId)) {
                continue;
            }
            double score = model.collaborative(productId, candidate.id());
            if (score > 0) {
                scores.put(candidate.id(), score);
            }
        }

        if (scores.size() < minimumRailSize()) {
            // Not enough shared browsing yet. General similarity is a better answer than a rail of
            // two items, and the heading still tells the truth.
            RecommendationRail fallback = similar(productId, limit);
            return new RecommendationRail(fallback.strategy(), "Customers also viewed",
                    "Close matches for " + anchor.name(), anchor.name(),
                    fallback.recommendationId(), fallback.items(), true,
                    "Few shoppers have browsed these together yet, so these are the closest matches.");
        }

        String reason = "Viewed alongside " + anchor.name();
        return rail("also_viewed", "Customers also viewed", null, anchor.name(), scores, limit,
                ignored -> reason, Set.of(productId), focusedDiversity());
    }

    /** What buyers actually put in the same basket. A stronger claim than browsing. */
    @Transactional(readOnly = true)
    public RecommendationRail alsoBought(Long productId, Integer requestedLimit) {
        int limit = clampLimit(requestedLimit);
        Model model = modelHolder.model();
        ProductFeatures anchor = model.features(productId);
        if (anchor == null) {
            return emptyRail("also_bought", "Frequently bought together", null);
        }

        Map<Long, Double> scores = new LinkedHashMap<>();
        for (ProductFeatures candidate : model.products()) {
            if (candidate.id().equals(productId)) {
                continue;
            }
            double affinity = model.purchaseAffinity(productId, candidate.id());
            if (affinity > 0) {
                scores.put(candidate.id(), affinity);
            }
        }

        if (scores.size() < minimumRailSize()) {
            return emptyRail("also_bought", "Frequently bought together", anchor.name());
        }

        Function<Long, String> reasons = candidateId -> {
            double affinity = model.purchaseAffinity(productId, candidateId);
            return Math.round(affinity * 100) + "% of buyers also bought this";
        };
        return rail("also_bought", "Frequently bought together",
                "Pairs well with " + anchor.name(), anchor.name(), scores, limit, reasons,
                Set.of(productId), focusedDiversity());
    }

    // ------------------------------------------------------------------
    // Shopper-anchored rails
    // ------------------------------------------------------------------

    /** Personalised rail. Falls back to trending, and says so, when there is no history to use. */
    @Transactional(readOnly = true)
    public RecommendationRail forActor(ActorContext actor, Integer requestedLimit) {
        int limit = clampLimit(requestedLimit);
        Model model = modelHolder.model();
        String actorKey = InteractionRecorder.actorKey(actor);

        List<HistoryItem> history = history(actorKey, 40);
        Set<Long> purchased = new LinkedHashSet<>(interactionRepository.findPurchasedProductIds(actorKey));

        if (history.isEmpty()) {
            RecommendationRail popular = trendingRail(limit);
            return new RecommendationRail("for_you", "Recommended for you",
                    "New here, so here is what everyone is buying", null, popular.recommendationId(),
                    popular.items(), true,
                    "No browsing history yet. These are the most popular products right now.");
        }

        double totalWeight = history.stream().mapToDouble(HistoryItem::decayed).sum();
        Map<String, Double> categoryAffinity = new HashMap<>();
        Map<String, Double> brandAffinity = new HashMap<>();
        for (HistoryItem item : history) {
            ProductFeatures features = model.features(item.productId());
            if (features == null) {
                continue;
            }
            categoryAffinity.merge(features.categorySlug(), item.decayed(), Double::sum);
            brandAffinity.merge(features.brand().toLowerCase(Locale.ROOT), item.decayed(), Double::sum);
        }
        normalise(categoryAffinity);
        normalise(brandAffinity);

        Set<Long> alreadyEngaged = new LinkedHashSet<>();
        for (HistoryItem item : history) {
            alreadyEngaged.add(item.productId());
        }

        Map<Long, Double> scores = new LinkedHashMap<>();
        Map<Long, HistoryItem> explanations = new HashMap<>();

        for (ProductFeatures candidate : model.products()) {
            if (purchased.contains(candidate.id())) {
                continue;
            }

            double behavioural = 0;
            HistoryItem explanation = null;
            double bestContribution = 0;
            for (HistoryItem item : history) {
                double contribution = item.decayed() * model.similarity(item.productId(), candidate.id());
                behavioural += contribution;
                if (contribution > bestContribution) {
                    bestContribution = contribution;
                    explanation = item;
                }
            }
            if (totalWeight > 0) {
                behavioural /= totalWeight;
            }

            double attribute = 0.7 * categoryAffinity.getOrDefault(candidate.categorySlug(), 0.0)
                    + 0.3 * brandAffinity.getOrDefault(candidate.brand().toLowerCase(Locale.ROOT), 0.0);

            double score = 0.55 * behavioural + 0.25 * attribute + 0.20 * model.trending(candidate.id());
            if (alreadyEngaged.contains(candidate.id())) {
                score *= VIEWED_PENALTY;
            }
            if (score > 0) {
                scores.put(candidate.id(), score);
                if (explanation != null && bestContribution > 0) {
                    explanations.put(candidate.id(), explanation);
                }
            }
        }

        if (scores.isEmpty()) {
            RecommendationRail popular = trendingRail(limit);
            return new RecommendationRail("for_you", "Recommended for you", null, null,
                    popular.recommendationId(), popular.items(), true,
                    "Nothing matched your recent browsing, so here is what is popular.");
        }

        Function<Long, String> reasons = candidateId -> {
            HistoryItem explanation = explanations.get(candidateId);
            if (explanation == null) {
                return "Popular right now";
            }
            ProductFeatures features = model.features(explanation.productId());
            if (features == null) {
                return "Popular right now";
            }
            return switch (explanation.type()) {
                case PURCHASE -> "Because you bought " + features.name();
                case CART_ADD -> "Because you added " + features.name() + " to your cart";
                case WISHLIST -> "Because you saved " + features.name();
                default -> "Because you viewed " + features.name();
            };
        };

        return rail("for_you", "Recommended for you", "Based on what you have been looking at",
                null, scores, limit, reasons, Set.of());
    }

    /** Time-decayed popularity. Also the cold-start answer for shoppers with no history. */
    @Transactional(readOnly = true)
    public RecommendationRail trending(Integer requestedLimit) {
        return trendingRail(clampLimit(requestedLimit));
    }

    /** What goes well with what is already in the basket, shown at the point of highest intent. */
    @Transactional(readOnly = true)
    public RecommendationRail cartCrossSell(ActorContext actor, Integer requestedLimit) {
        int limit = clampLimit(requestedLimit);
        Model model = modelHolder.model();

        Set<Long> inCart = new LinkedHashSet<>(interactionRepository
                .findActorHistory(InteractionRecorder.actorKey(actor)).stream()
                .filter(row -> row[1] == InteractionType.CART_ADD)
                .map(row -> ((Number) row[0]).longValue())
                .toList());

        if (inCart.isEmpty()) {
            return emptyRail("cart_cross_sell", "Complete your order", null);
        }

        Map<Long, Double> scores = new LinkedHashMap<>();
        Map<Long, Long> attributedTo = new HashMap<>();

        for (ProductFeatures candidate : model.products()) {
            if (inCart.contains(candidate.id())) {
                continue;
            }
            double best = 0;
            long bestAnchor = -1;
            for (Long anchor : inCart) {
                double affinity = model.purchaseAffinity(anchor, candidate.id());
                if (affinity > best) {
                    best = affinity;
                    bestAnchor = anchor;
                }
            }
            if (best > 0) {
                scores.put(candidate.id(), best);
                attributedTo.put(candidate.id(), bestAnchor);
            }
        }

        if (scores.isEmpty()) {
            // Nothing proven to be bought together yet. Recommending from the same categories is
            // better than leaving an empty slot at the moment the shopper is ready to pay.
            for (ProductFeatures candidate : model.products()) {
                if (inCart.contains(candidate.id())) {
                    continue;
                }
                double best = 0;
                long bestAnchor = -1;
                for (Long anchor : inCart) {
                    double score = model.similarity(anchor, candidate.id());
                    if (score > best) {
                        best = score;
                        bestAnchor = anchor;
                    }
                }
                if (best >= CROSS_SELL_SIMILARITY_FLOOR) {
                    scores.put(candidate.id(), best);
                    attributedTo.put(candidate.id(), bestAnchor);
                }
            }
        }

        if (scores.isEmpty()) {
            return emptyRail("cart_cross_sell", "Complete your order", null);
        }

        Function<Long, String> reasons = candidateId -> {
            Long anchor = attributedTo.get(candidateId);
            ProductFeatures features = anchor == null ? null : model.features(anchor);
            return features == null ? "Completes your order" : "Frequently bought with " + features.name();
        };
        return rail("cart_cross_sell", "Complete your order", "Shoppers usually add one of these",
                null, scores, limit, reasons, inCart, focusedDiversity());
    }

    // ------------------------------------------------------------------
    // Model health
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public RecommendationModelStatus status() {
        Model model = modelHolder.model();
        return new RecommendationModelStatus(
                properties.getRecommendations().isEnabled(),
                model.size(),
                model.supportedCount(),
                interactionRepository.count(),
                model.builtAt().toString());
    }

    @Transactional(readOnly = true)
    public List<PlacementPerformance> placementPerformance(int days) {
        Instant since = Instant.now().minus(days, ChronoUnit.DAYS);
        List<PlacementPerformance> performance = new ArrayList<>();
        for (Object[] row : interactionRepository.findClickThroughBySurface(since)) {
            long impressions = ((Number) row[1]).longValue();
            long clicks = ((Number) row[2]).longValue();
            double rate = impressions == 0 ? 0 : Math.min(1.0, (double) clicks / impressions);
            performance.add(new PlacementPerformance(String.valueOf(row[0]), impressions, clicks,
                    Math.round(rate * 1000) / 10.0));
        }
        return performance;
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private RecommendationRail trendingRail(int limit) {
        Model model = modelHolder.model();
        Map<Long, Double> scores = new LinkedHashMap<>();
        for (ProductFeatures candidate : model.products()) {
            double score = model.trending(candidate.id());
            if (score > 0) {
                scores.put(candidate.id(), score);
            }
        }

        if (scores.isEmpty()) {
            // A catalogue with no interactions at all. Rating is the only signal left, and an empty
            // rail would be a worse answer than a good guess.
            List<Product> fallback = productRepository.findCandidates(null, null, null, true,
                    PageRequest.of(0, limit, Sort.by(Sort.Order.desc("rating"), Sort.Order.desc("reviewCount"))));
            List<RecommendedProduct> items = fallback.stream()
                    .map(product -> new RecommendedProduct(productMapper.toSummary(product),
                            product.getRating().doubleValue(), "Highly rated", "trending"))
                    .toList();
            return new RecommendationRail("trending", "Popular right now", "Highest rated in stock",
                    null, UUID.randomUUID().toString(), items, true,
                    "No interaction history yet, so this is ranked by customer rating.");
        }

        return rail("trending", "Popular right now", "What shoppers are buying this week",
                null, scores, limit, ignored -> "Trending this week", Set.of());
    }

    /** Assembles a rail with its items, reasons and strategy label. */
    private RecommendationRail rail(String strategy,
                                    String title,
                                    String subtitle,
                                    String anchor,
                                    Map<Long, Double> scores,
                                    int limit,
                                    Function<Long, String> reasons,
                                    Set<Long> excluded) {
        return rail(strategy, title, subtitle, anchor, scores, limit, reasons, excluded,
                diversityLambda());
    }

    /** As above, with an explicit diversity weight. */
    private RecommendationRail rail(String strategy,
                                    String title,
                                    String subtitle,
                                    String anchor,
                                    Map<Long, Double> scores,
                                    int limit,
                                    Function<Long, String> reasons,
                                    Set<Long> excluded,
                                    double lambda) {
        Model model = modelHolder.model();

        Map<Long, Double> candidates = new LinkedHashMap<>();
        scores.forEach((productId, score) -> {
            if (!excluded.contains(productId) && model.contains(productId)) {
                candidates.put(productId, score);
            }
        });

        List<Long> chosen = new ArrayList<>();
        while (chosen.size() < limit && !candidates.isEmpty()) {
            Long best = null;
            double bestValue = Double.NEGATIVE_INFINITY;
            for (Map.Entry<Long, Double> entry : candidates.entrySet()) {
                double penalty = 0;
                for (Long selected : chosen) {
                    penalty = Math.max(penalty, model.similarity(entry.getKey(), selected));
                }
                double value = entry.getValue() - lambda * penalty;
                if (value > bestValue) {
                    bestValue = value;
                    best = entry.getKey();
                }
            }
            if (best == null) {
                break;
            }
            chosen.add(best);
            candidates.remove(best);
        }

        Map<Long, Product> products = new HashMap<>();
        productRepository.findByIdInAndActiveTrue(chosen)
                .forEach(product -> products.put(product.getId(), product));

        List<RecommendedProduct> items = new ArrayList<>();
        for (Long productId : chosen) {
            Product product = products.get(productId);
            if (product == null) {
                continue;
            }
            items.add(new RecommendedProduct(
                    productMapper.toSummary(product),
                    round(scores.getOrDefault(productId, 0.0)),
                    reasons.apply(productId),
                    strategy));
        }

        boolean thin = items.size() < minimumRailSize();
        return new RecommendationRail(strategy, title, subtitle, anchor,
                UUID.randomUUID().toString(), items, thin,
                thin ? "Not much history here yet, so these are the closest matches available." : null);
    }

    private RecommendationRail emptyRail(String strategy, String title, String anchor) {
        return new RecommendationRail(strategy, title, null, anchor,
                UUID.randomUUID().toString(), List.of(), true, "Nothing to recommend here yet.");
    }

    private List<HistoryItem> history(String actorKey, int maxItems) {
        List<HistoryItem> items = new ArrayList<>();
        Instant now = Instant.now();
        for (Object[] row : interactionRepository.findActorHistory(actorKey)) {
            Long productId = ((Number) row[0]).longValue();
            InteractionType type = (InteractionType) row[1];
            double weight = ((Number) row[2]).doubleValue();
            Instant at = (Instant) row[3];
            double ageDays = Math.max(0, Duration.between(at, now).toSeconds() / 86400.0);
            double decayed = weight * Math.pow(0.5, ageDays / HISTORY_HALF_LIFE_DAYS);
            items.add(new HistoryItem(productId, type, weight, at, decayed));
        }
        items.sort(Comparator.comparingDouble(HistoryItem::decayed).reversed());
        return items.size() > maxItems ? items.subList(0, maxItems) : items;
    }

    private void normalise(Map<String, Double> values) {
        double max = values.values().stream().mapToDouble(Double::doubleValue).max().orElse(0);
        if (max <= 0) {
            return;
        }
        values.replaceAll((key, value) -> value / max);
    }

    private int minimumRailSize() {
        return properties.getRecommendations().getMinimumUsefulRailSize();
    }

    private int clampLimit(Integer requested) {
        int fallback = properties.getRecommendations().getDefaultLimit();
        int max = properties.getRecommendations().getMaxLimit();
        if (requested == null || requested <= 0) {
            return Math.min(fallback, max);
        }
        return Math.min(requested, max);
    }

    private double diversityLambda() {
        return properties.getRecommendations().getDiversityLambda();
    }

    private double focusedDiversity() {
        return properties.getRecommendations().getFocusedDiversityLambda();
    }

    private double round(double value) {
        return Math.round(value * 10000) / 10000.0;
    }

    /** One past interaction, with its age already discounted. */
    private record HistoryItem(Long productId, InteractionType type, double weight, Instant at, double decayed) {
    }
}
