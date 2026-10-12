package com.shopwise.service;

import com.shopwise.config.ShopwiseProperties;
import com.shopwise.domain.Cart;
import com.shopwise.domain.CartItem;
import com.shopwise.domain.Product;
import com.shopwise.exception.ApiException;
import com.shopwise.repository.CartRepository;
import com.shopwise.recommendation.InteractionRecorder;
import com.shopwise.repository.ProductRepository;
import com.shopwise.security.ActorResolver.ActorContext;
import com.shopwise.web.dto.CartView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class CartService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    private final CartRepository cartRepository;
    private final ProductRepository productRepository;
    private final ShopwiseProperties properties;
    private final InteractionRecorder interactions;

    public CartService(CartRepository cartRepository,
                       ProductRepository productRepository,
                       ShopwiseProperties properties,
                       InteractionRecorder interactions) {
        this.cartRepository = cartRepository;
        this.productRepository = productRepository;
        this.properties = properties;
        this.interactions = interactions;
    }

    /**
     * Finds the caller's active cart, creating one on first use. Signed-in shoppers
     * get a durable cart keyed by user id; guests get one keyed by their guest token.
     */
    @Transactional
    public Cart resolveCart(ActorContext actor) {
        if (actor.authenticated()) {
            Optional<Cart> existing = cartRepository.findByUserIdAndStatus(actor.userId(), Cart.CartStatus.ACTIVE);
            if (existing.isPresent()) {
                return existing.get();
            }
            Cart cart = Cart.builder().userId(actor.userId()).status(Cart.CartStatus.ACTIVE).build();
            return cartRepository.save(cart);
        }
        if (actor.guestToken() == null) {
            throw ApiException.badRequest(
                    "A guest cart needs an X-Guest-Token header. Send any random string and reuse it.");
        }
        return cartRepository.findByGuestTokenAndStatus(actor.guestToken(), Cart.CartStatus.ACTIVE)
                .orElseGet(() -> cartRepository.save(Cart.builder()
                        .guestToken(actor.guestToken())
                        .status(Cart.CartStatus.ACTIVE)
                        .build()));
    }

    @Transactional
    public CartView view(ActorContext actor) {
        return toView(resolveCart(actor));
    }

    @Transactional
    public CartView addItem(ActorContext actor, Long productId, String sku, int quantity) {
        Cart cart = resolveCart(actor);
        Product product = locateProduct(productId, sku);

        if (!product.isActive()) {
            throw ApiException.badRequest(product.getName() + " is no longer available.");
        }
        if (!product.isInStock()) {
            throw ApiException.conflict(product.getName() + " is out of stock. We can notify you when it returns.");
        }

        CartItem existing = cart.getItems().stream()
                .filter(item -> item.getProduct().getId().equals(product.getId()))
                .findFirst()
                .orElse(null);

        int desired = (existing == null ? 0 : existing.getQuantity()) + Math.max(quantity, 1);
        int capped = Math.min(desired, Math.min(99, product.getStockQuantity()));

        if (existing == null) {
            CartItem item = CartItem.builder()
                    .cart(cart)
                    .product(product)
                    .quantity(capped)
                    .unitPrice(product.getPrice())
                    .build();
            cart.getItems().add(item);
        } else {
            existing.setQuantity(capped);
            existing.setUnitPrice(product.getPrice());
        }
        cartRepository.save(cart);
        // Recorded after the write succeeds, and in its own transaction, so a failure to log can
        // never cost the shopper their cart.
        interactions.cartAdd(actor, product.getId());
        return toView(cart);
    }

    @Transactional
    public CartView updateQuantity(ActorContext actor, Long productId, int quantity) {
        Cart cart = resolveCart(actor);
        CartItem item = cart.getItems().stream()
                .filter(line -> line.getProduct().getId().equals(productId))
                .findFirst()
                .orElseThrow(() -> ApiException.notFound("That product is not in your cart"));

        if (quantity <= 0) {
            cart.getItems().remove(item);
        } else {
            int capped = Math.min(quantity, Math.min(99, item.getProduct().getStockQuantity()));
            item.setQuantity(Math.max(capped, 1));
            item.setUnitPrice(item.getProduct().getPrice());
        }
        cartRepository.save(cart);
        return toView(cart);
    }

    @Transactional
    public CartView removeItem(ActorContext actor, Long productId) {
        Cart cart = resolveCart(actor);
        cart.getItems().removeIf(line -> line.getProduct().getId().equals(productId));
        cartRepository.save(cart);
        return toView(cart);
    }

    @Transactional
    public CartView clear(ActorContext actor) {
        Cart cart = resolveCart(actor);
        cart.getItems().clear();
        cartRepository.save(cart);
        return toView(cart);
    }

    /**
     * Moves a guest cart onto the account that just signed in, so nobody loses a
     * basket at the login step.
     */
    @Transactional
    public void mergeGuestCartInto(Long userId, String guestToken) {
        if (guestToken == null || userId == null) {
            return;
        }
        Optional<Cart> guestCart = cartRepository.findByGuestTokenAndStatus(guestToken, Cart.CartStatus.ACTIVE);
        if (guestCart.isEmpty() || guestCart.get().getItems().isEmpty()) {
            return;
        }
        Cart target = cartRepository.findByUserIdAndStatus(userId, Cart.CartStatus.ACTIVE)
                .orElseGet(() -> cartRepository.save(Cart.builder().userId(userId).status(Cart.CartStatus.ACTIVE).build()));

        for (CartItem guestItem : new ArrayList<>(guestCart.get().getItems())) {
            CartItem existing = target.getItems().stream()
                    .filter(item -> item.getProduct().getId().equals(guestItem.getProduct().getId()))
                    .findFirst()
                    .orElse(null);
            if (existing == null) {
                target.getItems().add(CartItem.builder()
                        .cart(target)
                        .product(guestItem.getProduct())
                        .quantity(guestItem.getQuantity())
                        .unitPrice(guestItem.getProduct().getPrice())
                        .build());
            } else {
                existing.setQuantity(Math.min(existing.getQuantity() + guestItem.getQuantity(), 99));
            }
        }
        guestCart.get().setStatus(Cart.CartStatus.ABANDONED);
        guestCart.get().getItems().clear();
        cartRepository.save(guestCart.get());
        cartRepository.save(target);
    }

    @Transactional
    public void markCheckedOut(Cart cart) {
        cart.setStatus(Cart.CartStatus.CHECKED_OUT);
        cartRepository.save(cart);
    }

    public BigDecimal shippingFeeFor(BigDecimal subtotal) {
        if (subtotal.compareTo(properties.getCommerce().getFreeShippingThreshold()) >= 0) {
            return ZERO;
        }
        return properties.getCommerce().getFlatShippingFee().setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal taxFor(BigDecimal subtotal) {
        return subtotal.multiply(properties.getCommerce().getTaxRate()).setScale(2, RoundingMode.HALF_UP);
    }

    private Product locateProduct(Long productId, String sku) {
        if (productId != null) {
            return productRepository.findWithCategoryById(productId)
                    .orElseThrow(() -> ApiException.notFound("Product " + productId));
        }
        if (sku != null && !sku.isBlank()) {
            return productRepository.findBySkuIgnoreCase(sku.trim())
                    .orElseThrow(() -> ApiException.notFound("SKU " + sku));
        }
        throw ApiException.badRequest("Provide either productId or sku.");
    }

    public CartView toView(Cart cart) {
        List<CartView.CartLine> lines = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        BigDecimal subtotal = ZERO;
        int itemCount = 0;

        for (CartItem item : cart.getItems()) {
            Product product = item.getProduct();
            boolean inStock = product.isInStock();
            boolean adjusted = item.getQuantity() > product.getStockQuantity();
            if (!inStock) {
                warnings.add(product.getName() + " is out of stock and will be removed at checkout.");
            } else if (adjusted) {
                warnings.add(product.getName() + " was reduced to the " + product.getStockQuantity()
                        + " units we have left.");
            } else if (product.getStockQuantity() <= properties.getCommerce().getLowStockThreshold()) {
                warnings.add("Only " + product.getStockQuantity() + " left of " + product.getName() + ".");
            }

            BigDecimal unitPrice = product.getPrice();
            BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(item.getQuantity()));
            subtotal = subtotal.add(lineTotal);
            itemCount += item.getQuantity();

            lines.add(new CartView.CartLine(
                    product.getId(),
                    product.getSku(),
                    product.getSlug(),
                    product.getName(),
                    product.getBrand(),
                    product.getImageUrl(),
                    product.getAccentColor(),
                    unitPrice,
                    item.getQuantity(),
                    lineTotal.setScale(2, RoundingMode.HALF_UP),
                    product.getStockQuantity(),
                    inStock,
                    adjusted));
        }

        subtotal = subtotal.setScale(2, RoundingMode.HALF_UP);
        BigDecimal shipping = lines.isEmpty() ? ZERO : shippingFeeFor(subtotal);
        BigDecimal tax = taxFor(subtotal);
        BigDecimal threshold = properties.getCommerce().getFreeShippingThreshold();
        BigDecimal remaining = threshold.subtract(subtotal).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);

        return new CartView(
                cart.getPublicId().toString(),
                cart.getGuestToken(),
                lines,
                itemCount,
                lines.size(),
                subtotal,
                shipping,
                tax,
                subtotal.add(shipping).add(tax).setScale(2, RoundingMode.HALF_UP),
                threshold,
                remaining,
                "USD",
                lines.isEmpty(),
                warnings.stream().distinct().toList());
    }
}