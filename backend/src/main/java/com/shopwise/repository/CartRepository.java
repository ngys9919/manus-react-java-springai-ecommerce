package com.shopwise.repository;

import com.shopwise.domain.Cart;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CartRepository extends JpaRepository<Cart, Long> {

    Optional<Cart> findByUserIdAndStatus(Long userId, Cart.CartStatus status);

    Optional<Cart> findByGuestTokenAndStatus(String guestToken, Cart.CartStatus status);

    Optional<Cart> findByPublicId(java.util.UUID publicId);
}
