package com.shopwise.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One recorded interaction between an actor and a product.
 *
 * <p>Append-only by design. Nothing here is updated or deleted, because every derived signal -
 * co-occurrence, trending, personal affinity - is a projection of this log and can be recomputed
 * if the model changes.</p>
 */
@Entity
@Table(name = "product_interaction")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductInteraction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "user_id")
    private Long userId;

    /** {@code user:<id>} when signed in, {@code guest:<token>} otherwise. */
    @Column(name = "actor_key", nullable = false, length = 80)
    private String actorKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "interaction", nullable = false, length = 24)
    private InteractionType interaction;

    /** Denormalised from {@link InteractionType#getWeight()} so aggregations are plain sums. */
    @Column(nullable = false, precision = 6, scale = 3)
    private BigDecimal weight;

    /** Groups one rendered rail with the clicks that followed, so click-through is measurable. */
    @Column(name = "recommendation_id", length = 64)
    private String recommendationId;

    /** Where the interaction happened: home, product_detail, cart, assistant, seed. */
    @Column(length = 40)
    private String surface;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /**
     * What the shopper did, and how much it says about preference.
     *
     * <p>The weights encode a rough intent ordering: a purchase is stronger evidence than a view,
     * and a cart add sits between them. Impressions and clicks carry no weight because they measure
     * the recommender rather than the shopper, so including them would let the system reinforce its
     * own suggestions.</p>
     */
    public enum InteractionType {
        VIEW(1.0),
        WISHLIST(2.5),
        CART_ADD(3.0),
        PURCHASE(5.0),
        RECO_IMPRESSION(0.0),
        RECO_CLICK(0.0);

        private final double weight;

        InteractionType(double weight) {
            this.weight = weight;
        }

        public double getWeight() {
            return weight;
        }

        public BigDecimal weightValue() {
            return BigDecimal.valueOf(weight);
        }

        /** True for the types that express shopper preference and feed the model. */
        public boolean isPreferenceSignal() {
            return weight > 0;
        }
    }
}
