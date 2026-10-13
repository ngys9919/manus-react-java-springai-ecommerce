package com.shopwise.repository;

import com.shopwise.domain.Cart;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CartRepository extends JpaRepository<Cart, Long> {

    Optional<Cart> findByUserIdAndStatus(Long userId, Cart.CartStatus status);

    Optional<Cart> findByGuestTokenAndStatus(String guestToken, Cart.CartStatus status);

    Optional<Cart> findByPublicId(java.util.UUID publicId);

    /**
     * Creates an active guest cart if the token does not already have one, in a single statement.
     *
     * <p>A find-then-insert cannot be made safe here. The storefront loads the cart, the assistant
     * reads it and the recommendation rails are fetched at the same time, so two requests for one
     * guest token routinely arrive together; both find nothing, both insert, and the second
     * violates {@code uq_cart_guest_active} and returns a 500 on a new visitor's first page view.
     * Catching the violation is not enough either, because the failed insert has already marked
     * the surrounding transaction rollback-only. Expressing the insert as an upsert removes the
     * race instead of handling it.</p>
     *
     * @return 1 when this call inserted the cart, 0 when another request had already created it
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            INSERT INTO cart (public_id, guest_token, status, created_at, updated_at)
            VALUES (:publicId, :guestToken, 'ACTIVE', now(), now())
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    int insertActiveGuestCartIfAbsent(@Param("publicId") UUID publicId, @Param("guestToken") String guestToken);

    /** The signed-in equivalent of {@link #insertActiveGuestCartIfAbsent}. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            INSERT INTO cart (public_id, user_id, status, created_at, updated_at)
            VALUES (:publicId, :userId, 'ACTIVE', now(), now())
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    int insertActiveUserCartIfAbsent(@Param("publicId") UUID publicId, @Param("userId") Long userId);
}