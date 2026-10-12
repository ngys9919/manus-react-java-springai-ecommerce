package com.shopwise.repository;

import com.shopwise.domain.Product;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    Optional<Product> findBySlugAndActiveTrue(String slug);

    Optional<Product> findBySkuIgnoreCase(String sku);

    List<Product> findBySkuInIgnoreCase(Collection<String> skus);

    List<Product> findByIdInAndActiveTrue(Collection<Long> ids);

    @Query("select p from Product p join fetch p.category where p.id = :id")
    Optional<Product> findWithCategoryById(@Param("id") Long id);

    Optional<Product> findByNameIgnoreCaseAndActiveTrue(String name);

    /**
     * Last-resort resolution for shopper-typed product names such as "Pulse 12 Pro" that
     * match neither SKU, slug nor exact name. Ordered by rating so the best-known match wins.
     */
    @Query("""
            select p from Product p
            where p.active = true and lower(p.name) like lower(concat('%', :needle, '%'))
            order by p.rating desc, p.reviewCount desc
            """)
    List<Product> searchByName(@Param("needle") String needle, Pageable pageable);

    @Query("""
            select p from Product p join fetch p.category c
            where p.active = true
              and (:categorySlug is null or c.slug = :categorySlug)
              and (:minPrice is null or p.price >= :minPrice)
              and (:maxPrice is null or p.price <= :maxPrice)
              and (:inStockOnly = false or p.stockQuantity > 0)
            """)
    List<Product> findCandidates(@Param("categorySlug") String categorySlug,
                                 @Param("minPrice") BigDecimal minPrice,
                                 @Param("maxPrice") BigDecimal maxPrice,
                                 @Param("inStockOnly") boolean inStockOnly,
                                 Pageable pageable);

    @Query("select min(p.price) from Product p where p.active = true")
    BigDecimal findMinPrice();

    @Query("select max(p.price) from Product p where p.active = true")
    BigDecimal findMaxPrice();

    long countByActiveTrue();
}
