package com.shopwise.web.dto;

import java.math.BigDecimal;
import java.util.List;

public record ProductPage(
        List<ProductSummary> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext,
        boolean hasPrevious,
        String sort,
        String appliedQuery,
        Facets facets) {

    public record Facets(
            List<CategoryFacet> categories,
            BigDecimal minPrice,
            BigDecimal maxPrice) {
    }

    public record CategoryFacet(String slug, String name, String icon, long count) {
    }
}