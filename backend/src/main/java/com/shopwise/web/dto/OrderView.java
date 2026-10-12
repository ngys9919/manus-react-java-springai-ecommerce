package com.shopwise.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderView(
        String reference,
        String status,
        String statusLabel,
        Instant placedAt,
        BigDecimal subtotal,
        BigDecimal shippingFee,
        BigDecimal taxTotal,
        BigDecimal grandTotal,
        String currency,
        String paymentMethod,
        AddressView shippingAddress,
        List<OrderLineView> items,
        int itemCount,
        Instant estimatedDelivery,
        String trackingNote) {

    public record AddressView(
            String fullName,
            String line1,
            String line2,
            String city,
            String postcode,
            String country) {
    }

    public record OrderLineView(
            Long productId,
            String sku,
            String productName,
            String imageUrl,
            String slug,
            BigDecimal unitPrice,
            int quantity,
            BigDecimal lineTotal) {
    }
}