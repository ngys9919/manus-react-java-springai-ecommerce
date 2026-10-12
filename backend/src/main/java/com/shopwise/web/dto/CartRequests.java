package com.shopwise.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public final class CartRequests {

    private CartRequests() {
    }

    public record AddItem(
            Long productId,
            String sku,
            @Min(value = 1, message = "quantity must be at least 1")
            @Max(value = 99, message = "quantity must be 99 or less")
            Integer quantity) {

        public int quantityOrDefault() {
            return quantity == null ? 1 : quantity;
        }
    }

    public record UpdateQuantity(
            @NotNull(message = "quantity is required")
            @Min(value = 0, message = "quantity cannot be negative")
            @Max(value = 99, message = "quantity must be 99 or less")
            Integer quantity) {
    }
}