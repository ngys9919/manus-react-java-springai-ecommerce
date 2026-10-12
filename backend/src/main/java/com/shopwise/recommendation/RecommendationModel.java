package com.shopwise.recommendation;

import com.shopwise.config.ShopwiseProperties;
import com.shopwise.recommendation.RecommendationDataLoader.ProductFeatures;
import com.shopwise.recommendation.RecommendationDataLoader.RecommendationData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Similarity between products, rebuilt from the interaction log and cached.
 *
 * <p>Two signals are combined:</p>
 *
 * <ul>
 *   <li><b>Collaborative</b> - item-item cosine similarity over co-occurrence. Two products are
 *       similar when the same shoppers engaged with both. This captures relationships nobody wrote
 *       down, such as a particular laptop and a particular pair of headphones being considered
 *       together.</li>
 *   <li><b>Content</b> - a weighted comparison of category, brand, price and spec tokens. This
 *       works for a product with no history at all, which is the case every time something is
 *       added to the catalogue.</li>
 * </ul>
 *
 * <p>Which one wins depends on how much behaviour exists. A product with plenty of interactions is
 * described by what shoppers actually did; a new one is described by what it is. Blending by the
 * weaker of the two items' confidence means a rail can mix established and new products without
 * either being misrepresented.</p>
 */
@Component
public class RecommendationModel {

    private static final Logger log = LoggerFactory.getLogger(RecommendationModel.class);

    private final RecommendationDataLoader loader;
    private final ShopwiseProperties properties;

    private volatile Model cached;
    private final Object lock = new Object();

    public RecommendationModel(RecommendationDataLoader loader, ShopwiseProperties properties) {
        this.loader = loader;
        this.properties = properties;
    }

    /** The current model, rebuilt when the cached copy has aged out. */
    public Model model() {
        Model current = cached;
        if (current != null && !current.isStale(properties.getRecommendations().getModelTtlSeconds())) {
            return current;
        }
        synchronized (lock) {
            if (cached != null && !cached.isStale(properties.getRecommendations().getModelTtlSeconds())) {
                return cached;
            }
            cached = build(loader.load());
            log.debug("Recommendation model rebuilt: {} products, {} with behaviour",
                    cached.size(), cached.supportedCount());
            return cached;
        }
    }

    /** Discards the cache. Called after writes that should be reflected immediately. */
    public void invalidate() {
        cached = null;
    }

    // ------------------------------------------------------------------
    // Construction
    // ------------------------------------------------------------------

    private Model build(RecommendationData data) {
        Map<Long, ProductFeatures> features = new LinkedHashMap<>();
        for (ProductFeatures product : data.products()) {
            features.put(product.id(), product);
        }

        Map<Long, Double> norms = new HashMap<>();
        data.itemActorWeights().forEach((productId, actorWeights) -> {
            double sumSquares = 0;
            for (double weight : actorWeights.values()) {
                sumSquares += weight * weight;
            }
            norms.put(productId, Math.sqrt(sumSquares));
        });

        Map<Long, Map<Long, Double>> collaborative = collaborativeSimilarity(data.itemActorWeights(), norms);
        Map<Long, Map<Long, Double>> content = contentSimilarity(features.values());

        double maxTrending = data.trending().values().stream()
                .mapToDouble(Double::doubleValue)
                .max()
                .orElse(0);

        Map<Long, Double> trendingNormalised = new HashMap<>();
        data.trending().forEach((productId, score) ->
                trendingNormalised.put(productId, maxTrending <= 0 ? 0 : score / maxTrending));

        Map<Long, Double> confidence = new HashMap<>();
        int saturation = Math.max(1, properties.getRecommendations().getSupportSaturation());
        for (Long productId : features.keySet()) {
            double support = data.support().getOrDefault(productId, 0.0);
            confidence.put(productId, Math.min(1.0, support / saturation));
        }

        Map<Long, Map<Long, Double>> purchaseAffinity =
                purchaseAffinity(data.purchasePairs(), data.purchaseCounts());

        return new Model(features, collaborative, content, confidence,
                trendingNormalised, purchaseAffinity, data.loadedAt(), Instant.now());
    }

    /**
     * How strongly two products are bought together.
     *
     * <p>Measured as the share of the less popular item's buyers who also bought the other. Using
     * the smaller count rather than both makes the number symmetric and interpretable: "of the
     * people who bought this, that share also bought the other one".</p>
     */
    private Map<Long, Map<Long, Double>> purchaseAffinity(Map<Long, Map<Long, Double>> pairs,
                                                          Map<Long, Double> purchaseCounts) {
        Map<Long, Map<Long, Double>> affinity = new HashMap<>();
        pairs.forEach((left, neighbours) -> neighbours.forEach((right, together) -> {
            double denominator = Math.min(
                    purchaseCounts.getOrDefault(left, 0.0),
                    purchaseCounts.getOrDefault(right, 0.0));
            if (denominator <= 0) {
                return;
            }
            double score = Math.min(1.0, together / denominator);
            affinity.computeIfAbsent(left, key -> new HashMap<>()).put(right, score);
        }));
        return affinity;
    }

    /**
     * Item-item cosine over the co-occurrence matrix.
     *
     * <p>For every shopper, each pair of products they engaged with contributes the product of their
     * weights. Dividing by the two vector norms stops a single product that everybody looked at from
     * appearing similar to everything.</p>
     */
    private Map<Long, Map<Long, Double>> collaborativeSimilarity(
            Map<Long, Map<String, Double>> itemActorWeights, Map<Long, Double> norms) {

        // Invert to actor -> (product, weight), which is the order pairs are walked in.
        Map<String, List<long[]>> byActorIndex = new HashMap<>();
        Map<String, List<Double>> byActorWeight = new HashMap<>();
        itemActorWeights.forEach((productId, actorWeights) -> actorWeights.forEach((actorKey, weight) -> {
            byActorIndex.computeIfAbsent(actorKey, key -> new ArrayList<>()).add(new long[]{productId});
            byActorWeight.computeIfAbsent(actorKey, key -> new ArrayList<>()).add(weight);
        }));

        Map<Long, Map<Long, Double>> dotProducts = new HashMap<>();
        byActorIndex.forEach((actorKey, items) -> {
            List<Double> weights = byActorWeight.get(actorKey);
            for (int i = 0; i < items.size(); i++) {
                for (int j = i + 1; j < items.size(); j++) {
                    long left = items.get(i)[0];
                    long right = items.get(j)[0];
                    if (left == right) {
                        continue;
                    }
                    double contribution = weights.get(i) * weights.get(j);
                    accumulate(dotProducts, left, right, contribution);
                    accumulate(dotProducts, right, left, contribution);
                }
            }
        });

        Map<Long, Map<Long, Double>> similarity = new HashMap<>();
        dotProducts.forEach((left, neighbours) -> neighbours.forEach((right, dot) -> {
            double leftNorm = norms.getOrDefault(left, 0.0);
            double rightNorm = norms.getOrDefault(right, 0.0);
            if (leftNorm <= 0 || rightNorm <= 0) {
                return;
            }
            double cosine = dot / (leftNorm * rightNorm);
            if (cosine > 0) {
                similarity.computeIfAbsent(left, key -> new HashMap<>()).put(right, cosine);
            }
        }));
        return similarity;
    }

    private void accumulate(Map<Long, Map<Long, Double>> target, long from, long to, double value) {
        target.computeIfAbsent(from, key -> new HashMap<>()).merge(to, value, Double::sum);
    }

    /**
     * Weighted content similarity between every pair of products.
     *
     * <p>Category carries the most weight because it is the strongest statement of what a product
     * is for. Price closeness matters because shoppers comparing two laptops are usually choosing
     * between comparable ones. Spec tokens pick up the rest.</p>
     */
    private Map<Long, Map<Long, Double>> contentSimilarity(Collection<ProductFeatures> products) {
        List<ProductFeatures> list = new ArrayList<>(products);
        Map<Long, Map<Long, Double>> similarity = new HashMap<>();

        for (int i = 0; i < list.size(); i++) {
            for (int j = i + 1; j < list.size(); j++) {
                ProductFeatures left = list.get(i);
                ProductFeatures right = list.get(j);
                double score = contentScore(left, right);
                if (score <= 0) {
                    continue;
                }
                similarity.computeIfAbsent(left.id(), key -> new HashMap<>()).put(right.id(), score);
                similarity.computeIfAbsent(right.id(), key -> new HashMap<>()).put(left.id(), score);
            }
        }
        return similarity;
    }

    private double contentScore(ProductFeatures left, ProductFeatures right) {
        double category = left.categorySlug().equals(right.categorySlug()) ? 1.0 : 0.0;
        double brand = left.brand().equalsIgnoreCase(right.brand()) ? 1.0 : 0.0;

        double maxPrice = Math.max(left.price(), right.price());
        double priceCloseness = maxPrice <= 0
                ? 1.0
                : 1.0 - Math.min(1.0, Math.abs(left.price() - right.price()) / maxPrice);

        double tokenOverlap = jaccard(left.tokens(), right.tokens());

        return 0.40 * category + 0.15 * brand + 0.20 * priceCloseness + 0.25 * tokenOverlap;
    }

    private double jaccard(Set<String> left, Set<String> right) {
        if (left.isEmpty() || right.isEmpty()) {
            return 0.0;
        }
        int intersection = 0;
        Set<String> smaller = left.size() <= right.size() ? left : right;
        Set<String> larger = smaller == left ? right : left;
        for (String token : smaller) {
            if (larger.contains(token)) {
                intersection++;
            }
        }
        int union = left.size() + right.size() - intersection;
        return union == 0 ? 0.0 : (double) intersection / union;
    }

    // ------------------------------------------------------------------
    // The model itself
    // ------------------------------------------------------------------

    /** Immutable snapshot. Safe to read from any thread while a newer one is being built. */
    public static final class Model {

        private final Map<Long, ProductFeatures> features;
        private final Map<Long, Map<Long, Double>> collaborative;
        private final Map<Long, Map<Long, Double>> content;
        private final Map<Long, Double> confidence;
        private final Map<Long, Double> trending;
        private final Map<Long, Map<Long, Double>> purchaseAffinity;
        private final Instant dataLoadedAt;
        private final Instant builtAt;

        Model(Map<Long, ProductFeatures> features,
              Map<Long, Map<Long, Double>> collaborative,
              Map<Long, Map<Long, Double>> content,
              Map<Long, Double> confidence,
              Map<Long, Double> trending,
              Map<Long, Map<Long, Double>> purchaseAffinity,
              Instant dataLoadedAt,
              Instant builtAt) {
            this.features = features;
            this.collaborative = collaborative;
            this.content = content;
            this.confidence = confidence;
            this.trending = trending;
            this.purchaseAffinity = purchaseAffinity;
            this.dataLoadedAt = dataLoadedAt;
            this.builtAt = builtAt;
        }

        /** Share of the less popular item's buyers who also bought the other. Zero when unproven. */
        public double purchaseAffinity(Long left, Long right) {
            return purchaseAffinity.getOrDefault(left, Map.of()).getOrDefault(right, 0.0);
        }

        boolean isStale(int ttlSeconds) {
            return Instant.now().isAfter(builtAt.plusSeconds(Math.max(1, ttlSeconds)));
        }

        public int size() {
            return features.size();
        }

        public long supportedCount() {
            return confidence.values().stream().filter(value -> value > 0).count();
        }

        public Instant builtAt() {
            return builtAt;
        }

        public Instant dataLoadedAt() {
            return dataLoadedAt;
        }

        public Collection<ProductFeatures> products() {
            return features.values();
        }

        public ProductFeatures features(Long productId) {
            return features.get(productId);
        }

        public boolean contains(Long productId) {
            return features.containsKey(productId);
        }

        public double support(Long productId) {
            return confidence.getOrDefault(productId, 0.0);
        }

        public double trending(Long productId) {
            return trending.getOrDefault(productId, 0.0);
        }

        public double collaborative(Long left, Long right) {
            return collaborative.getOrDefault(left, Map.of()).getOrDefault(right, 0.0);
        }

        public double content(Long left, Long right) {
            if (left.equals(right)) {
                return 1.0;
            }
            return content.getOrDefault(left, Map.of()).getOrDefault(right, 0.0);
        }

        /**
         * The similarity used for ranking and for diversity.
         *
         * <p>Without any co-occurrence evidence there is nothing behavioural to blend, so the
         * content score stands alone. Otherwise the two are mixed by the weaker item's confidence,
         * so a well-understood pair is described by behaviour and a cold pair by attributes.</p>
         */
        public double similarity(Long left, Long right) {
            if (left.equals(right)) {
                return 1.0;
            }
            double contentScore = content(left, right);
            double collaborativeScore = collaborative(left, right);
            if (collaborativeScore <= 0) {
                return contentScore;
            }
            double trust = Math.min(confidence.getOrDefault(left, 0.0), confidence.getOrDefault(right, 0.0));
            return trust * collaborativeScore + (1 - trust) * contentScore;
        }

        /** Similar products, strongest first. */
        public List<Scored> neighbours(Long productId, int limit) {
            if (!features.containsKey(productId)) {
                return List.of();
            }
            List<Scored> scored = new ArrayList<>();
            for (Long candidate : features.keySet()) {
                if (candidate.equals(productId)) {
                    continue;
                }
                double score = similarity(productId, candidate);
                if (score > 0) {
                    scored.add(new Scored(candidate, score));
                }
            }
            scored.sort(Comparator.comparingDouble(Scored::score).reversed());
            return scored.size() > limit ? scored.subList(0, limit) : scored;
        }
    }

    /** A candidate product and the strength of its relationship to something else. */
    public record Scored(Long productId, double score) {
    }
}
