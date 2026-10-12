package com.shopwise.web.controller;

import com.shopwise.security.ActorResolver;
import com.shopwise.security.ActorResolver.ActorContext;
import com.shopwise.service.CartService;
import com.shopwise.web.dto.CartRequests;
import com.shopwise.web.dto.CartView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cart endpoints. Guests are first-class citizens: send an {@code X-Guest-Token}
 * header (any stable random string) and the cart persists across reloads.
 */
@RestController
@RequestMapping("/api/v1/cart")
@Tag(name = "Cart", description = "Guest and signed-in cart management")
public class CartController {

    private final CartService cartService;
    private final ActorResolver actorResolver;

    public CartController(CartService cartService, ActorResolver actorResolver) {
        this.cartService = cartService;
        this.actorResolver = actorResolver;
    }

    @GetMapping
    @Operation(summary = "Read the current cart with totals and stock warnings")
    public CartView cart(HttpServletRequest request) {
        return cartService.view(actor(request));
    }

    @PostMapping("/items")
    @Operation(summary = "Add a product by id or SKU; quantity is clamped to available stock")
    public CartView add(HttpServletRequest request, @Valid @RequestBody CartRequests.AddItem body) {
        return cartService.addItem(actor(request), body.productId(), body.sku(), body.quantityOrDefault());
    }

    @PatchMapping("/items/{productId}")
    @Operation(summary = "Set the quantity of a cart line; 0 removes it")
    public CartView update(HttpServletRequest request,
                           @PathVariable Long productId,
                           @Valid @RequestBody CartRequests.UpdateQuantity body) {
        return cartService.updateQuantity(actor(request), productId, body.quantity());
    }

    @DeleteMapping("/items/{productId}")
    @Operation(summary = "Remove a product from the cart")
    public CartView remove(HttpServletRequest request, @PathVariable Long productId) {
        return cartService.removeItem(actor(request), productId);
    }

    @DeleteMapping
    @Operation(summary = "Empty the cart")
    public CartView clear(HttpServletRequest request) {
        return cartService.clear(actor(request));
    }

    private ActorContext actor(HttpServletRequest request) {
        return actorResolver.resolve(request);
    }
}