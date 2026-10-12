package com.shopwise.web.controller;

import com.shopwise.exception.ApiException;
import com.shopwise.security.ActorResolver;
import com.shopwise.security.ActorResolver.ActorContext;
import com.shopwise.service.AuthService;
import com.shopwise.web.dto.AuthDtos.AuthResponse;
import com.shopwise.web.dto.AuthDtos.LoginRequest;
import com.shopwise.web.dto.AuthDtos.RegisterRequest;
import com.shopwise.web.dto.AuthDtos.UserView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Authentication", description = "Register, sign in and read the current identity")
public class AuthController {

    private final AuthService authService;
    private final ActorResolver actorResolver;

    public AuthController(AuthService authService, ActorResolver actorResolver) {
        this.authService = authService;
        this.actorResolver = actorResolver;
    }

    @PostMapping("/auth/register")
    @Operation(summary = "Create an account; any guest cart is merged into the new account")
    public AuthResponse register(HttpServletRequest request, @Valid @RequestBody RegisterRequest body) {
        ActorContext actor = actorResolver.resolve(request);
        return authService.register(body, actor.guestToken());
    }

    @PostMapping("/auth/login")
    @Operation(summary = "Sign in and receive a bearer token; any guest cart is merged")
    public AuthResponse login(HttpServletRequest request, @Valid @RequestBody LoginRequest body) {
        ActorContext actor = actorResolver.resolve(request);
        return authService.login(body, actor.guestToken());
    }

    @GetMapping("/me")
    @Operation(summary = "Current identity, used by the storefront header and the staff gate")
    public UserView me(HttpServletRequest request) {
        ActorContext actor = actorResolver.resolve(request);
        if (!actor.authenticated()) {
            throw ApiException.forbidden("You are browsing as a guest.");
        }
        return authService.me(actor.userId());
    }
}