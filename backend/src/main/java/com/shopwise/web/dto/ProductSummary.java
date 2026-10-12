package com.shopwise.web.dto;

import java.math.BigDecimal;
import java.util.List;

/** Card-sized product projection used by grids, search results and the assistant. */
public record ProductSummary(
        Long id,
        String sku,
        String slug,
        String name,
        String brand,
        String categorySlug,
        String categoryName,
        BigDecimal price,
        BigDecimal compareAtPrice,
        Integer discountPercent,
        String currency,
        int stockQuantity,
        boolean inStock,
        boolean lowStock,
        BigDecimal rating,
        int reviewCount,
        String imageUrl,
        String accentColor,
        String shortDescription,
        List<String> tags) {
}