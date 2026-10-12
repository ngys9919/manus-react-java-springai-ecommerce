package com.shopwise.service;

import com.shopwise.domain.AppUser;
import com.shopwise.exception.ApiException;
import com.shopwise.repository.AppUserRepository;
import com.shopwise.security.JwtService;
import com.shopwise.web.dto.AuthDtos.AuthResponse;
import com.shopwise.web.dto.AuthDtos.LoginRequest;
import com.shopwise.web.dto.AuthDtos.RegisterRequest;
import com.shopwise.web.dto.AuthDtos.UserView;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class AuthService {

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final CartService cartService;

    public AuthService(AppUserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       CartService cartService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.cartService = cartService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request, String guestToken) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("An account already exists for " + email + ". Try signing in instead.");
        }
        AppUser user = userRepository.save(AppUser.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(request.password()))
                .fullName(request.fullName().trim())
                .role(AppUser.Role.CUSTOMER)
                .build());

        cartService.mergeGuestCartInto(user.getId(), guestToken);
        return issue(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request, String guestToken) {
        AppUser user = userRepository.findByEmailIgnoreCase(request.email().trim())
                .orElseThrow(() -> new ApiException(org.springframework.http.HttpStatus.UNAUTHORIZED,
                        "INVALID_CREDENTIALS", "That email and password combination does not match an account."));
        if (!user.isEnabled()) {
            throw ApiException.forbidden("This account has been disabled. Contact support for help.");
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ApiException(org.springframework.http.HttpStatus.UNAUTHORIZED,
                    "INVALID_CREDENTIALS", "That email and password combination does not match an account.");
        }
        cartService.mergeGuestCartInto(user.getId(), guestToken);
        return issue(user);
    }

    @Transactional(readOnly = true)
    public UserView me(Long userId) {
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("Account"));
        return toView(user);
    }

    private AuthResponse issue(AppUser user) {
        return new AuthResponse(jwtService.issue(user), "Bearer", jwtService.tokenTtlSeconds(), toView(user));
    }

    private UserView toView(AppUser user) {
        boolean staff = user.getRole() == AppUser.Role.SUPPORT || user.getRole() == AppUser.Role.ADMIN;
        return new UserView(user.getId(), user.getEmail(), user.getFullName(), user.getRole().name(), staff);
    }
}