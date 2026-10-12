package com.shopwise.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "shop_order")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShopOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 24)
    private String reference;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "guest_email")
    private String guestEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    @Builder.Default
    private OrderStatus status = OrderStatus.CONFIRMED;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "shipping_fee", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal shippingFee = BigDecimal.ZERO;

    @Column(name = "tax_total", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal taxTotal = BigDecimal.ZERO;

    @Column(name = "grand_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal grandTotal;

    @Column(nullable = false, length = 3)
    @Builder.Default
    private String currency = "USD";

    @Column(name = "payment_method", nullable = false, length = 32)
    @Builder.Default
    private String paymentMethod = "CARD_DEMO";

    @Column(name = "shipping_name", nullable = false, length = 160)
    private String shippingName;

    @Column(name = "shipping_line1", nullable = false, length = 200)
    private String shippingLine1;

    @Column(name = "shipping_line2", length = 200)
    private String shippingLine2;

    @Column(name = "shipping_city", nullable = false, length = 120)
    private String shippingCity;

    @Column(name = "shipping_postcode", nullable = false, length = 32)
    private String shippingPostcode;

    @Column(name = "shipping_country", nullable = false, length = 80)
    private String shippingCountry;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("id ASC")
    @Builder.Default
    private List<OrderItem> items = new ArrayList<>();

    @Column(name = "placed_at", nullable = false)
    @Builder.Default
    private Instant placedAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public enum OrderStatus {
        CONFIRMED,
        PACKING,
        SHIPPED,
        DELIVERED,
        CANCELLED
    }
}
