package com.shopwise.service;

import com.shopwise.domain.Cart;
import com.shopwise.domain.CartItem;
import com.shopwise.domain.OrderItem;
import com.shopwise.domain.Product;
import com.shopwise.domain.ShopOrder;
import com.shopwise.exception.ApiException;
import com.shopwise.repository.CartRepository;
import com.shopwise.repository.OrderRepository;
import com.shopwise.recommendation.InteractionRecorder;
import com.shopwise.repository.ProductRepository;
import com.shopwise.security.ActorResolver.ActorContext;
import com.shopwise.web.dto.CheckoutRequest;
import com.shopwise.web.dto.OrderView;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class OrderService {

    private static final DateTimeFormatter REFERENCE_DATE = DateTimeFormatter.ofPattern("yyMMdd");

    private final OrderRepository orderRepository;
    private final CartRepository cartRepository;
    private final ProductRepository productRepository;
    private final CartService cartService;
    private final InteractionRecorder interactions;

    public OrderService(OrderRepository orderRepository,
                        CartRepository cartRepository,
                        ProductRepository productRepository,
                        CartService cartService,
                        InteractionRecorder interactions) {
        this.orderRepository = orderRepository;
        this.cartRepository = cartRepository;
        this.productRepository = productRepository;
        this.cartService = cartService;
        this.interactions = interactions;
    }

    @Transactional
    public OrderView checkout(ActorContext actor, CheckoutRequest request) {
        Cart cart = cartService.resolveCart(actor);
        if (cart.getItems().isEmpty()) {
            throw ApiException.badRequest("Your cart is empty. Add something before checking out.");
        }

        List<OrderItem> orderItems = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;

        ShopOrder order = ShopOrder.builder()
                .reference(nextReference())
                .userId(actor.userId())
                .guestEmail(actor.authenticated() ? actor.email() : request.email())
                .status(ShopOrder.OrderStatus.CONFIRMED)
                .paymentMethod("CARD_DEMO")
                .shippingName(request.fullName())
                .shippingLine1(request.line1())
                .shippingLine2(request.line2())
                .shippingCity(request.city())
                .shippingPostcode(request.postcode())
                .shippingCountry(request.country())
                .build();

        for (CartItem item : cart.getItems()) {
            Product product = productRepository.findWithCategoryById(item.getProduct().getId())
                    .orElseThrow(() -> ApiException.notFound("Product " + item.getProduct().getId()));
            if (product.getStockQuantity() < item.getQuantity()) {
                throw ApiException.conflict(product.getName() + " only has " + product.getStockQuantity()
                        + " units left. Reduce the quantity to continue.");
            }
            BigDecimal unitPrice = product.getPrice();
            BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(item.getQuantity()))
                    .setScale(2, RoundingMode.HALF_UP);
            subtotal = subtotal.add(lineTotal);

            orderItems.add(OrderItem.builder()
                    .order(order)
                    .productId(product.getId())
                    .sku(product.getSku())
                    .productName(product.getName())
                    .imageUrl(product.getImageUrl())
                    .unitPrice(unitPrice)
                    .quantity(item.getQuantity())
                    .lineTotal(lineTotal)
                    .build());

            product.setStockQuantity(product.getStockQuantity() - item.getQuantity());
            productRepository.save(product);
        }

        subtotal = subtotal.setScale(2, RoundingMode.HALF_UP);
        BigDecimal shipping = cartService.shippingFeeFor(subtotal);
        BigDecimal tax = cartService.taxFor(subtotal);

        order.setItems(orderItems);
        order.setSubtotal(subtotal);
        order.setShippingFee(shipping);
        order.setTaxTotal(tax);
        order.setGrandTotal(subtotal.add(shipping).add(tax).setScale(2, RoundingMode.HALF_UP));

        ShopOrder saved = orderRepository.save(order);
        cartService.markCheckedOut(cart);
        // The strongest preference signal there is. Recorded per product so that
        // "frequently bought together" has pairs to work with.
        interactions.purchase(actor, orderItems.stream()
                .map(OrderItem::getProductId)
                .filter(java.util.Objects::nonNull)
                .toList());
        return toView(saved);
    }

    @Transactional(readOnly = true)
    public List<OrderView> forUser(Long userId, int limit) {
        return orderRepository.findByUserIdOrderByPlacedAtDesc(userId, PageRequest.of(0, Math.clamp(limit, 1, 50)))
                .getContent().stream()
                .map(this::toView)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<OrderView> latestForUser(Long userId) {
        return orderRepository.findTop5ByUserIdOrderByPlacedAtDesc(userId).stream()
                .findFirst()
                .map(this::toView);
    }

    @Transactional(readOnly = true)
    public OrderView byReference(String reference) {
        return orderRepository.findByReferenceIgnoreCase(reference)
                .map(this::toView)
                .orElseThrow(() -> ApiException.notFound("Order " + reference));
    }

    /**
     * Order lookup for the assistant. A signed-in shopper may only see their own
     * orders; guests must supply the email used at checkout. This is the assistant's
     * main permission boundary.
     */
    @Transactional(readOnly = true)
    public OrderView lookupForAssistant(ActorContext actor, String reference, String email) {
        ShopOrder order = orderRepository.findByReferenceIgnoreCase(reference)
                .orElseThrow(() -> ApiException.notFound("Order " + reference));

        if (actor.authenticated()) {
            if (!order.getUserId().equals(actor.userId())) {
                throw ApiException.forbidden("That order belongs to a different account. "
                        + "Sign in with the account used at checkout, or contact support.");
            }
        } else {
            boolean emailMatches = email != null
                    && order.getGuestEmail() != null
                    && order.getGuestEmail().equalsIgnoreCase(email.trim());
            if (!emailMatches) {
                throw ApiException.forbidden("To look up a guest order I need the email address used at checkout.");
            }
        }
        return toView(order);
    }

    @Transactional(readOnly = true)
    public List<OrderView> recentForAssistant(ActorContext actor, int limit) {
        if (!actor.authenticated()) {
            return List.of();
        }
        return orderRepository.findTop5ByUserIdOrderByPlacedAtDesc(actor.userId()).stream()
                .limit(Math.clamp(limit, 1, 5))
                .map(this::toView)
                .toList();
    }

    public OrderView toView(ShopOrder order) {
        List<OrderView.OrderLineView> lines = order.getItems().stream()
                .map(item -> new OrderView.OrderLineView(
                        item.getProductId(),
                        item.getSku(),
                        item.getProductName(),
                        item.getImageUrl(),
                        slugFor(item.getProductId()),
                        item.getUnitPrice(),
                        item.getQuantity(),
                        item.getLineTotal()))
                .toList();

        int itemCount = order.getItems().stream().mapToInt(OrderItem::getQuantity).sum();
        Instant estimatedDelivery = order.getPlacedAt().plusSeconds(deliveryDays(order.getStatus()) * 86400L);

        return new OrderView(
                order.getReference(),
                order.getStatus().name(),
                statusLabel(order.getStatus()),
                order.getPlacedAt(),
                order.getSubtotal(),
                order.getShippingFee(),
                order.getTaxTotal(),
                order.getGrandTotal(),
                order.getCurrency(),
                order.getPaymentMethod(),
                new OrderView.AddressView(
                        order.getShippingName(),
                        order.getShippingLine1(),
                        order.getShippingLine2(),
                        order.getShippingCity(),
                        order.getShippingPostcode(),
                        order.getShippingCountry()),
                lines,
                itemCount,
                estimatedDelivery,
                trackingNote(order.getStatus()));
    }

    private String slugFor(Long productId) {
        if (productId == null) {
            return null;
        }
        return productRepository.findById(productId).map(Product::getSlug).orElse(null);
    }

    private long deliveryDays(ShopOrder.OrderStatus status) {
        return switch (status) {
            case CONFIRMED -> 5;
            case PACKING -> 4;
            case SHIPPED -> 2;
            case DELIVERED -> 0;
            case CANCELLED -> 0;
        };
    }

    private String statusLabel(ShopOrder.OrderStatus status) {
        return switch (status) {
            case CONFIRMED -> "Confirmed";
            case PACKING -> "Packing";
            case SHIPPED -> "On the way";
            case DELIVERED -> "Delivered";
            case CANCELLED -> "Cancelled";
        };
    }

    private String trackingNote(ShopOrder.OrderStatus status) {
        return switch (status) {
            case CONFIRMED -> "We are preparing your parcel. Tracking appears once it leaves the warehouse.";
            case PACKING -> "Being packed now. Tracking appears within 24 hours.";
            case SHIPPED -> "Handed to the courier — track it from the confirmation email.";
            case DELIVERED -> "Delivered. Returns are open for 30 days.";
            case CANCELLED -> "Cancelled and refunded to the original payment method.";
        };
    }

    private String nextReference() {
        String prefix = "SW-" + LocalDate.now(ZoneOffset.UTC).format(REFERENCE_DATE) + "-";
        long existing = orderRepository.countByReferenceStartingWith(prefix);
        return prefix + String.format(Locale.ROOT, "%04d", existing + 1);
    }
}