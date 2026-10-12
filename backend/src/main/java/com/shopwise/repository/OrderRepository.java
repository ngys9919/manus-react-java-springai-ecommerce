package com.shopwise.repository;

import com.shopwise.domain.ShopOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<ShopOrder, Long> {

    Optional<ShopOrder> findByReferenceIgnoreCase(String reference);

    Page<ShopOrder> findByUserIdOrderByPlacedAtDesc(Long userId, Pageable pageable);

    List<ShopOrder> findTop5ByUserIdOrderByPlacedAtDesc(Long userId);

    List<ShopOrder> findTop5ByGuestEmailIgnoreCaseOrderByPlacedAtDesc(String email);

    long countByReferenceStartingWith(String prefix);
}
