package com.shopwise.web.mapper;

import com.shopwise.config.ShopwiseProperties;
import com.shopwise.domain.Product;
import com.shopwise.web.dto.ProductDetail;
import com.shopwise.web.dto.ProductSummary;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Component
public class ProductMapper {

    private final ShopwiseProperties properties;

    public ProductMapper(ShopwiseProperties properties) {
        this.properties = properties;
    }

    public ProductSummary toSummary(Product product) {
        return new ProductSummary(
                product.getId(),
                product.getSku(),
                product.getSlug(),
                product.getName(),
                product.getBrand(),
                product.getCategory().getSlug(),
                product.getCategory().getName(),
                product.getPrice(),
                product.getCompareAtPrice(),
                discountPercent(product),
                product.getCurrency(),
                product.getStockQuantity(),
                product.isInStock(),
                product.getStockQuantity() > 0
                        && product.getStockQuantity() <= properties.getCommerce().getLowStockThreshold(),
                product.getRating(),
                product.getReviewCount(),
                product.getImageUrl(),
                product.getAccentColor(),
                product.getShortDescription(),
                tagList(product.getTags()));
    }

    public ProductDetail toDetail(Product product) {
        return new ProductDetail(
                product.getId(),
                product.getSku(),
                product.getSlug(),
                product.getName(),
                product.getBrand(),
                product.getCategory().getSlug(),
                product.getCategory().getName(),
                product.getPrice(),
                product.getCompareAtPrice(),
                discountPercent(product),
                product.getCurrency(),
                product.getStockQuantity(),
                product.isInStock(),
                product.getStockQuantity() > 0
                        && product.getStockQuantity() <= properties.getCommerce().getLowStockThreshold(),
                product.getRating(),
                product.getReviewCount(),
                product.getImageUrl(),
                product.getAccentColor(),
                product.getShortDescription(),
                product.getDescription(),
                tagList(product.getTags()),
                product.getHighlights() == null ? List.of() : List.copyOf(product.getHighlights()),
                product.getAttributes() == null ? Map.of() : product.getAttributes());
    }

    private Integer discountPercent(Product product) {
        BigDecimal compareAt = product.getCompareAtPrice();
        if (compareAt == null || compareAt.compareTo(product.getPrice()) <= 0) {
            return null;
        }
        BigDecimal saved = compareAt.subtract(product.getPrice());
        return saved.multiply(BigDecimal.valueOf(100))
                .divide(compareAt, 0, RoundingMode.HALF_UP)
                .intValue();
    }

    private List<String> tagList(String tags) {
        if (tags == null || tags.isBlank()) {
            return List.of();
        }
        return Arrays.stream(tags.split(","))
                .map(String::trim)
                .filter(tag -> !tag.isEmpty())
                .toList();
    }
}