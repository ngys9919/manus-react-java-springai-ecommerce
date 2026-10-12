package com.shopwise.web.controller;

import com.shopwise.service.CatalogService;
import com.shopwise.web.dto.CategoryView;
import com.shopwise.web.dto.ProductDetail;
import com.shopwise.web.dto.ProductPage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/catalog")
@Tag(name = "Catalog", description = "Public product catalogue: search, facets and product detail")
public class CatalogController {

    private final CatalogService catalogService;

    public CatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping("/products")
    @Operation(summary = "Search products with filters, sorting and facets")
    public ProductPage products(
            @Parameter(description = "Free-text query across name, brand, tags and category")
            @RequestParam(required = false) String q,

            @Parameter(description = "Category slug, or 'all'")
            @RequestParam(required = false) String category,

            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,

            @Parameter(description = "Only show products that are in stock")
            @RequestParam(required = false) Boolean inStock,

            @Parameter(description = "relevance | price-asc | price-desc | rating | newest | name")
            @RequestParam(defaultValue = "relevance") String sort,

            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {

        return catalogService.search(q, category, minPrice, maxPrice, inStock, sort, page, size);
    }

    @GetMapping("/products/{slugOrSku}")
    @Operation(summary = "Product detail by slug or SKU")
    public ProductDetail product(@PathVariable String slugOrSku) {
        return catalogService.detail(slugOrSku);
    }

    @GetMapping("/categories")
    @Operation(summary = "Categories with product counts and entry prices")
    public List<CategoryView> categories() {
        return catalogService.categories();
    }
}