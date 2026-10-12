package com.shopwise.web.controller;

import com.shopwise.exception.ApiException;
import com.shopwise.security.ActorResolver;
import com.shopwise.security.ActorResolver.ActorContext;
import com.shopwise.service.OrderService;
import com.shopwise.web.dto.CheckoutRequest;
import com.shopwise.web.dto.OrderView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/orders")
@Tag(name = "Orders", description = "Guest and signed-in checkout, order history and tracking")
public class OrderController {

    private final OrderService orderService;
    private final ActorResolver actorResolver;

    public OrderController(OrderService orderService, ActorResolver actorResolver) {
        this.orderService = orderService;
        this.actorResolver = actorResolver;
    }

    @PostMapping
    @Operation(summary = "Check out the current cart; guests must supply an email")
    public OrderView checkout(HttpServletRequest request, @Valid @RequestBody CheckoutRequest body) {
        return orderService.checkout(actorResolver.resolve(request), body);
    }

    @GetMapping("/mine")
    @Operation(summary = "Order history for the signed-in shopper")
    public List<OrderView> mine(HttpServletRequest request,
                                @RequestParam(defaultValue = "20") int limit) {
        ActorContext actor = actorResolver.resolve(request);
        if (!actor.authenticated()) {
            throw ApiException.forbidden("Sign in to see your order history.");
        }
        return orderService.forUser(actor.userId(), limit);
    }

    @GetMapping("/{reference}/track")
    @Operation(summary = "Track an order. Signed-in shoppers see their own; guests must pass the checkout email")
    public OrderView track(HttpServletRequest request,
                           @PathVariable String reference,
                           @RequestParam(required = false) String email) {
        return orderService.lookupForAssistant(actorResolver.resolve(request), reference, email);
    }
}