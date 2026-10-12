package com.shopwise.web.dto;

import java.math.BigDecimal;
import java.util.List;

public record CartView(
        String id,
        String guestToken,
        List<CartLine> items,
        int itemCount,
        int distinctItemCount,
        BigDecimal subtotal,
        BigDecimal shippingFee,
        BigDecimal taxTotal,
        BigDecimal grandTotal,
        BigDecimal freeShippingThreshold,
        BigDecimal freeShippingRemaining,
        String currency,
        boolean empty,
        List<String> warnings) {

    public record CartLine(
            Long productId,
            String sku,
            String slug,
            String name,
            String brand,
            String imageUrl,
            String accentColor,
            BigDecimal unitPrice,
            int quantity,
            BigDecimal lineTotal,
            int stockQuantity,
            boolean inStock,
            boolean quantityAdjusted) {
    }
}