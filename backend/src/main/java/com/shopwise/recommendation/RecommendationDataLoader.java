package com.shopwise.recommendation;

import com.shopwise.config.ShopwiseProperties;
import com.shopwise.domain.Product;
import com.shopwise.domain.ProductInteraction;
import com.shopwise.repository.ProductInteractionRepository;
import com.shopwise.repository.ProductRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Reads everything the similarity model needs, inside one transaction, and hands it back as plain
 * data.
 *
 * <p>This exists as its own bean for a reason: the model is cached across requests, so it must not
 * retain JPA entities whose lazy associations would blow up once the session closed. Loading is
 * transactional here, and everything the model keeps is copied into records that have no
 * relationship to the persistence context. It also avoids the self-invocation trap, where calling a
 * {@code @Transactional} method from inside the same bean silently skips the proxy.</p>
 */
@Component
public class RecommendationDataLoader {

    private final ProductRepository productRepository;
    private final ProductInteractionRepository interactionRepository;
    private final ShopwiseProperties properties;

    public RecommendationDataLoader(ProductRepository productRepository,
                                    ProductInteractionRepository interactionRepository,
                                    ShopwiseProperties properties) {
        this.productRepository = productRepository;
        this.interactionRepository = interactionRepository;
        this.properties = properties;
    }

    /** Immutable, detached view of the catalogue and its behaviour. */
    public record RecommendationData(
            List<ProductFeatures> products,
            Map<Long, Map<String, Double>> itemActorWeights,
            Map<Long, Double> support,
            Map<Long, Double> trending,
            Map<Long, Double> purchaseCounts,
            Map<Long, Map<Long, Double>> purchasePairs,
            Instant loadedAt) {

        public static RecommendationData empty() {
            return new RecommendationData(
                    List.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Instant.now());
        }
    }

    /**
     * Attribute summary of one product, used for content similarity.
     *
     * <p>{@code tokens} folds the tags and the spec sheet into one bag of words, which is what lets
     * "16 GB unified memory" and "16GB RAM" contribute to similarity without a schema for either.</p>
     */
    public record ProductFeatures(
            Long id,
            String sku,
            String name,
            String categorySlug,
            String brand,
            double price,
            Set<String> tokens) {
    }

    @Transactional(readOnly = true)
    public RecommendationData load() {
        Instant since = Instant.now().minus(properties.getRecommendations().getSimilarityWindowDays(),
                ChronoUnit.DAYS);

        List<ProductFeatures> features = productRepository.findAll().stream()
                .filter(Product::isActive)
                .map(this::toFeatures)
                .toList();

        Map<Long, Map<String, Double>> itemActorWeights = new HashMap<>();
        for (Object[] row : interactionRepository.findPreferenceMatrix(since)) {
            String actorKey = (String) row[0];
            Long productId = ((Number) row[1]).longValue();
            double weight = ((Number) row[2]).doubleValue();
            if (weight <= 0) {
                continue;
            }
            itemActorWeights.computeIfAbsent(productId, key -> new HashMap<>()).merge(actorKey, weight, Double::sum);
        }

        Map<Long, Double> support = new HashMap<>();
        for (Object[] row : interactionRepository.findSupportByProduct(since)) {
            support.put(((Number) row[0]).longValue(), ((Number) row[1]).doubleValue());
        }

        Map<Long, Double> trending = new HashMap<>();
        double halfLife = properties.getRecommendations().getTrendingHalfLifeDays();
        for (Object[] row : interactionRepository.findTrendingScores(since, halfLife)) {
            trending.put(((Number) row[0]).longValue(), ((Number) row[1]).doubleValue());
        }

        Map<Long, Double> purchaseCounts = new HashMap<>();
        for (Object[] row : interactionRepository.findPurchaseCounts(since)) {
            purchaseCounts.put(((Number) row[0]).longValue(), ((Number) row[1]).doubleValue());
        }

        Map<Long, Map<Long, Double>> purchasePairs = new HashMap<>();
        for (Object[] row : interactionRepository.findPurchasePairs(since)) {
            long left = ((Number) row[0]).longValue();
            long right = ((Number) row[1]).longValue();
            double together = ((Number) row[2]).doubleValue();
            purchasePairs.computeIfAbsent(left, key -> new HashMap<>()).put(right, together);
            purchasePairs.computeIfAbsent(right, key -> new HashMap<>()).put(left, together);
        }

        return new RecommendationData(features, itemActorWeights, support, trending,
                purchaseCounts, purchasePairs, Instant.now());
    }

    private ProductFeatures toFeatures(Product product) {
        Set<String> tokens = new LinkedHashSet<>();
        if (product.getTags() != null) {
            for (String tag : product.getTags().split(",")) {
                addToken(tokens, tag);
            }
        }
        Map<String, String> attributes = product.getAttributes();
        if (attributes != null) {
            attributes.forEach((key, value) -> {
                addToken(tokens, key);
                if (value != null) {
                    addToken(tokens, String.valueOf(value));
                }
            });
        }
        if (product.getShortDescription() != null) {
            for (String word : product.getShortDescription().split("[^A-Za-z0-9]+")) {
                addToken(tokens, word);
            }
        }

        return new ProductFeatures(
                product.getId(),
                product.getSku(),
                product.getName(),
                product.getCategory().getSlug(),
                product.getBrand(),
                product.getPrice().doubleValue(),
                tokens);
    }

    /** Lower-cases, trims, and drops tokens too short or too generic to discriminate. */
    private void addToken(Set<String> tokens, String raw) {
        if (raw == null) {
            return;
        }
        String token = raw.trim().toLowerCase(Locale.ROOT);
        if (token.length() < 3 || token.length() > 40 || GENERIC_TOKENS.contains(token)) {
            return;
        }
        tokens.add(token);
    }

    /**
     * Words that appear on so many products that matching on them says nothing. Without this,
     * "wireless" and "with" would make unrelated products look similar.
     */
    private static final Set<String> GENERIC_TOKENS = Set.of(
            "the", "and", "with", "for", "your", "that", "this", "from", "into",
            "plus", "new", "all", "per", "via", "has", "its", "our");

    /** Kept for symmetry with the loader's contract; unused but documents the intent. */
    static List<ProductFeatures> sortedById(List<ProductFeatures> features) {
        List<ProductFeatures> copy = new ArrayList<>(features);
        copy.sort((left, right) -> Long.compare(left.id(), right.id()));
        return copy;
    }
}
