package com.shopwise.web.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Full product page projection including the comparison spec sheet. */
public record ProductDetail(
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
        String description,
        List<String> tags,
        List<String> highlights,
        Map<String, String> attributes) {
}