package com.shopwise.repository;

import com.shopwise.domain.ProductInteraction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface ProductInteractionRepository extends JpaRepository<ProductInteraction, Long> {

    long countByInteraction(ProductInteraction.InteractionType interaction);

    @Query("select count(distinct i.actorKey) from ProductInteraction i")
    long countDistinctActors();

    /**
     * The whole preference matrix as (actor, product, summed weight) triples.
     *
     * <p>Small enough to hold in memory at this catalogue size, and doing so lets the similarity
     * model blend collaborative and content signals in one place. A catalogue in the thousands
     * would precompute this into a similarity table instead.</p>
     */
    @Query("""
            select i.actorKey, i.product.id, sum(i.weight)
            from ProductInteraction i
            where i.weight > 0 and i.createdAt >= :since
            group by i.actorKey, i.product.id
            """)
    List<Object[]> findPreferenceMatrix(@Param("since") Instant since);

    /** How many distinct actors have expressed an interest in each product. */
    @Query("""
            select i.product.id, count(distinct i.actorKey)
            from ProductInteraction i
            where i.weight > 0 and i.createdAt >= :since
            group by i.product.id
            """)
    List<Object[]> findSupportByProduct(@Param("since") Instant since);

    /**
     * Time-decayed popularity. The exponential decay means a product that was popular three weeks
     * ago ranks below one gaining attention today, which is what "trending" should mean.
     */
    @Query(value = """
            SELECT i.product_id,
                   sum(i.weight * exp(-extract(epoch FROM (now() - i.created_at)) / 86400.0 / :halfLifeDays)) AS score
            FROM product_interaction i
            WHERE i.weight > 0 AND i.created_at >= :since
            GROUP BY i.product_id
            ORDER BY score DESC
            """, nativeQuery = true)
    List<Object[]> findTrendingScores(@Param("since") Instant since,
                                      @Param("halfLifeDays") double halfLifeDays);

    /** One actor's own history, newest first, for personalisation and for excluding repeats. */
    @Query("""
            select i.product.id, i.interaction, i.weight, i.createdAt
            from ProductInteraction i
            where i.actorKey = :actorKey and i.weight > 0
            order by i.createdAt desc
            """)
    List<Object[]> findActorHistory(@Param("actorKey") String actorKey);

    /** Distinct products an actor has already bought, so they are never recommended again. */
    @Query("""
            select distinct i.product.id
            from ProductInteraction i
            where i.actorKey = :actorKey and i.interaction = com.shopwise.domain.ProductInteraction$InteractionType.PURCHASE
            """)
    List<Long> findPurchasedProductIds(@Param("actorKey") String actorKey);

    @Query("""
            select i.product.id, count(distinct i.actorKey)
            from ProductInteraction i
            where i.interaction = com.shopwise.domain.ProductInteraction$InteractionType.PURCHASE
              and i.createdAt >= :since
            group by i.product.id
            """)
    List<Object[]> findPurchaseCounts(@Param("since") Instant since);

    /**
     * Product pairs bought by the same shopper, with the number of shoppers who bought both.
     *
     * <p>Restricted to purchases on purpose. General co-occurrence answers "people who looked at
     * this also looked at that", which is a browsing question. Bundling needs the narrower signal:
     * both items actually left the shop together.</p>
     */
    @Query(value = """
            SELECT a.product_id, b.product_id, count(DISTINCT a.actor_key)
            FROM product_interaction a
            JOIN product_interaction b
              ON a.actor_key = b.actor_key
             AND a.product_id < b.product_id
            WHERE a.interaction = 'PURCHASE'
              AND b.interaction = 'PURCHASE'
              AND a.created_at >= :since
              AND b.created_at >= :since
            GROUP BY a.product_id, b.product_id
            """, nativeQuery = true)
    List<Object[]> findPurchasePairs(@Param("since") Instant since);

    // --- measurement -----------------------------------------------------

    @Query("""
            select count(i) from ProductInteraction i
            where i.interaction = com.shopwise.domain.ProductInteraction$InteractionType.RECO_IMPRESSION
              and i.createdAt >= :since
            """)
    long countImpressionsSince(@Param("since") Instant since);

    @Query("""
            select count(i) from ProductInteraction i
            where i.interaction = com.shopwise.domain.ProductInteraction$InteractionType.RECO_CLICK
              and i.createdAt >= :since
            """)
    long countClicksSince(@Param("since") Instant since);

    /** Impressions per surface, so a rail that nobody looks at can be identified. */
    @Query("""
            select coalesce(i.surface, 'unknown'), count(i)
            from ProductInteraction i
            where i.interaction = com.shopwise.domain.ProductInteraction$InteractionType.RECO_IMPRESSION
              and i.createdAt >= :since
            group by i.surface
            order by count(i) desc
            """)
    List<Object[]> findImpressionCountsBySurface(@Param("since") Instant since);

    /** Click-through per surface: the number that says whether a placement earns its space. */
    @Query("""
            select coalesce(i.surface, 'unknown'),
                   count(i) filter (where i.interaction = com.shopwise.domain.ProductInteraction$InteractionType.RECO_IMPRESSION),
                   count(i) filter (where i.interaction = com.shopwise.domain.ProductInteraction$InteractionType.RECO_CLICK)
            from ProductInteraction i
            where i.recommendationId is not null and i.createdAt >= :since
            group by i.surface
            order by 2 desc
            """)
    List<Object[]> findClickThroughBySurface(@Param("since") Instant since);
}
