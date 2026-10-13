package com.shopwise.service;

import com.shopwise.config.ShopwiseProperties;
import com.shopwise.domain.Category;
import com.shopwise.domain.Product;
import com.shopwise.exception.ApiException;
import com.shopwise.repository.CategoryRepository;
import com.shopwise.repository.ProductRepository;
import com.shopwise.web.dto.CategoryView;
import com.shopwise.web.dto.ProductDetail;
import com.shopwise.web.dto.ProductPage;
import com.shopwise.web.dto.ProductSummary;
import com.shopwise.web.mapper.ProductMapper;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class CatalogService {

    private static final int MAX_PAGE_SIZE = 60;

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductMapper mapper;
    private final ShopwiseProperties properties;

    public CatalogService(ProductRepository productRepository,
                          CategoryRepository categoryRepository,
                          ProductMapper mapper,
                          ShopwiseProperties properties) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.mapper = mapper;
        this.properties = properties;
    }

    public ProductPage search(String query,
                              String categorySlug,
                              BigDecimal minPrice,
                              BigDecimal maxPrice,
                              Boolean inStockOnly,
                              String sortKey,
                              int page,
                              int size) {

        int safePage = Math.max(page, 0);
        int safeSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
        Sort sort = resolveSort(sortKey);
        Pageable pageable = PageRequest.of(safePage, safeSize, sort);

        Specification<Product> specification = buildSpecification(query, categorySlug, minPrice, maxPrice, inStockOnly);
        Page<Product> result = productRepository.findAll(specification, pageable);

        return new ProductPage(
                result.getContent().stream().map(mapper::toSummary).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.hasNext(),
                result.hasPrevious(),
                sortKey == null ? "relevance" : sortKey,
                query == null ? "" : query,
                buildFacets(query, minPrice, maxPrice, inStockOnly));
    }

    private Specification<Product> buildSpecification(String query,
                                                      String categorySlug,
                                                      BigDecimal minPrice,
                                                      BigDecimal maxPrice,
                                                      Boolean inStockOnly) {
        return (root, criteriaQuery, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.isTrue(root.get("active")));

            if (categorySlug != null && !categorySlug.isBlank() && !"all".equalsIgnoreCase(categorySlug)) {
                predicates.add(builder.equal(root.get("category").get("slug"), categorySlug));
            }
            if (minPrice != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("price"), minPrice));
            }
            if (maxPrice != null) {
                predicates.add(builder.lessThanOrEqualTo(root.get("price"), maxPrice));
            }
            if (Boolean.TRUE.equals(inStockOnly)) {
                predicates.add(builder.greaterThan(root.get("stockQuantity"), 0));
            }
            if (query != null && !query.isBlank()) {
                // Every word must match somewhere; the words need not be adjacent. Matching the
                // query as one phrase meant "TitanForge X16 gaming laptop" found nothing while
                // "TitanForge X16" found the product, so one extra descriptive word was enough to
                // make the shopper think the catalogue did not stock it.
                for (String term : query.trim().toLowerCase(Locale.ROOT).split("\\s+")) {
                    if (term.isEmpty()) {
                        continue;
                    }
                    String like = "%" + term + "%";
                    predicates.add(builder.or(
                            builder.like(builder.lower(root.get("name")), like),
                            builder.like(builder.lower(root.get("brand")), like),
                            builder.like(builder.lower(root.get("shortDescription")), like),
                            builder.like(builder.lower(root.get("tags")), like),
                            builder.like(builder.lower(root.get("sku")), like),
                            builder.like(builder.lower(root.get("category").get("name")), like)));
                }
            }
            return builder.and(predicates.toArray(new Predicate[0]));
        };
    }

    private Sort resolveSort(String sortKey) {
        if (sortKey == null) {
            return Sort.by(Sort.Order.desc("rating"), Sort.Order.asc("price"));
        }
        return switch (sortKey.toLowerCase(Locale.ROOT)) {
            case "price-asc" -> Sort.by(Sort.Order.asc("price"));
            case "price-desc" -> Sort.by(Sort.Order.desc("price"));
            case "rating" -> Sort.by(Sort.Order.desc("rating"), Sort.Order.desc("reviewCount"));
            case "newest" -> Sort.by(Sort.Order.desc("createdAt"));
            case "name" -> Sort.by(Sort.Order.asc("name"));
            default -> Sort.by(Sort.Order.desc("rating"), Sort.Order.asc("price"));
        };
    }

    private ProductPage.Facets buildFacets(String query, BigDecimal minPrice, BigDecimal maxPrice, Boolean inStockOnly) {
        Specification<Product> specification = buildSpecification(query, null, minPrice, maxPrice, inStockOnly);
        List<Product> matching = productRepository.findAll(specification);

        Map<String, Category> bySlug = new LinkedHashMap<>();
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Product product : matching) {
            Category category = product.getCategory();
            bySlug.put(category.getSlug(), category);
            counts.merge(category.getSlug(), 1L, Long::sum);
        }

        List<ProductPage.CategoryFacet> facets = bySlug.values().stream()
                .map(category -> new ProductPage.CategoryFacet(
                        category.getSlug(),
                        category.getName(),
                        category.getIcon(),
                        counts.getOrDefault(category.getSlug(), 0L)))
                .sorted(Comparator.comparingLong(ProductPage.CategoryFacet::count).reversed())
                .toList();

        BigDecimal facetMin = matching.stream().map(Product::getPrice).min(BigDecimal::compareTo)
                .orElseGet(() -> productRepository.findMinPrice());
        BigDecimal facetMax = matching.stream().map(Product::getPrice).max(BigDecimal::compareTo)
                .orElseGet(() -> productRepository.findMaxPrice());

        return new ProductPage.Facets(facets, facetMin, facetMax);
    }

    public ProductDetail detail(String slugOrSku) {
        Product product = productRepository.findBySlugAndActiveTrue(slugOrSku)
                .or(() -> productRepository.findBySkuIgnoreCase(slugOrSku).filter(Product::isActive))
                .orElseThrow(() -> ApiException.notFound("Product '" + slugOrSku + "'"));
        return mapper.toDetail(product);
    }

    public ProductDetail detailById(Long id) {
        Product product = productRepository.findWithCategoryById(id)
                .filter(Product::isActive)
                .orElseThrow(() -> ApiException.notFound("Product " + id));
        return mapper.toDetail(product);
    }

    public List<CategoryView> categories() {
        List<Product> all = productRepository.findAll();
        Map<String, BigDecimal> priceFrom = new LinkedHashMap<>();
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Product product : all) {
            if (!product.isActive()) {
                continue;
            }
            String slug = product.getCategory().getSlug();
            counts.merge(slug, 1L, Long::sum);
            priceFrom.merge(slug, product.getPrice(), BigDecimal::min);
        }
        return categoryRepository.findAllByOrderByDisplayOrderAsc().stream()
                .map(category -> new CategoryView(
                        category.getId(),
                        category.getSlug(),
                        category.getName(),
                        category.getDescription(),
                        category.getIcon(),
                        counts.getOrDefault(category.getSlug(), 0L),
                        priceFrom.containsKey(category.getSlug())
                                ? priceFrom.get(category.getSlug()).toPlainString()
                                : null))
                .toList();
    }

    // ------------------------------------------------------------------
    // Assistant-facing lookups
    // ------------------------------------------------------------------

    /** Free-text search used by the assistant's catalog tool. */
    public List<ProductSummary> searchForAssistant(String query,
                                                   String categorySlug,
                                                   BigDecimal minPrice,
                                                   BigDecimal maxPrice,
                                                   Boolean inStockOnly,
                                                   Integer limit) {
        int effectiveLimit = Math.clamp(limit == null ? properties.getAssistant().getToolResultLimit() : limit,
                1, 20);
        Specification<Product> specification = buildSpecification(query, categorySlug, minPrice, maxPrice, inStockOnly);
        Pageable pageable = PageRequest.of(0, effectiveLimit, Sort.by(Sort.Order.desc("rating"), Sort.Order.asc("price")));
        return productRepository.findAll(specification, pageable).getContent().stream()
                .map(mapper::toSummary)
                .toList();
    }

    public List<ProductSummary> bySkus(Collection<String> skus) {
        if (skus == null || skus.isEmpty()) {
            return List.of();
        }
        List<String> normalized = skus.stream()
                .filter(sku -> sku != null && !sku.isBlank())
                .map(sku -> sku.trim().toUpperCase(Locale.ROOT))
                .toList();
        if (normalized.isEmpty()) {
            return List.of();
        }
        List<Product> found = productRepository.findBySkuInIgnoreCase(normalized);
        Map<String, Product> bySku = new LinkedHashMap<>();
        found.forEach(product -> bySku.put(product.getSku().toUpperCase(Locale.ROOT), product));
        // Preserve the order the caller asked for so comparisons read predictably.
        return normalized.stream()
                .map(bySku::get)
                .filter(java.util.Objects::nonNull)
                .map(mapper::toSummary)
                .toList();
    }

    public List<ProductSummary> byIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return productRepository.findByIdInAndActiveTrue(ids).stream()
                .map(mapper::toSummary)
                .toList();
    }

    public Optional<ProductSummary> bySku(String sku) {
        if (sku == null || sku.isBlank()) {
            return Optional.empty();
        }
        return productRepository.findBySkuIgnoreCase(sku.trim())
                .filter(Product::isActive)
                .map(mapper::toSummary);
    }

    public Product requireProduct(Long productId) {
        return productRepository.findWithCategoryById(productId)
                .filter(Product::isActive)
                .orElseThrow(() -> ApiException.notFound("Product " + productId));
    }

    /** Spec sheets for a set of products, used to build comparison tables. */
    public List<ProductDetail> detailsForComparison(List<String> skus) {
        List<ProductSummary> summaries = bySkus(skus);
        return summaries.stream()
                .map(summary -> productRepository.findWithCategoryById(summary.id())
                        .map(mapper::toDetail)
                        .orElse(null))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    /** What a comparison request resolved to, and what it could not resolve. */
    public record ComparisonResolution(List<ProductDetail> products, List<String> unresolved) {
    }

    /**
     * Resolves comparison targets from whatever the shopper (or the model) actually said.
     *
     * <p>SKUs, slugs, exact names and finally partial names are all accepted, so a request such
     * as "compare the Pulse 12 Pro and the Vertex Ultra 5G" resolves in a single tool call instead
     * of failing and forcing the model to search first. Order is preserved and duplicates are
     * dropped, so the resulting table always reads left-to-right as the shopper asked.</p>
     */
    public ComparisonResolution resolveForComparison(List<String> identifiers) {
        List<ProductDetail> products = new ArrayList<>();
        List<String> unresolved = new ArrayList<>();
        List<Long> seen = new ArrayList<>();

        for (String raw : identifiers == null ? List.<String>of() : identifiers) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String identifier = raw.trim();
            Product product = productRepository.findBySkuIgnoreCase(identifier)
                    .filter(Product::isActive)
                    .or(() -> productRepository.findBySlugAndActiveTrue(identifier))
                    .or(() -> productRepository.findByNameIgnoreCaseAndActiveTrue(identifier))
                    .orElse(null);

            if (product == null) {
                List<Product> fuzzy = productRepository.searchByName(identifier, PageRequest.of(0, 1));
                product = fuzzy.isEmpty() ? null : fuzzy.get(0);
            }

            if (product == null) {
                unresolved.add(identifier);
                continue;
            }
            if (seen.contains(product.getId())) {
                continue;
            }
            seen.add(product.getId());
            productRepository.findWithCategoryById(product.getId())
                    .map(mapper::toDetail)
                    .ifPresent(products::add);
        }
        return new ComparisonResolution(products, unresolved);
    }
}
