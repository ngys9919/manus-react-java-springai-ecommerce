package com.shopwise.web.dto;

public record CategoryView(
        Long id,
        String slug,
        String name,
        String description,
        String icon,
        long productCount,
        String priceFrom) {
}