package com.shopwise.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Resolves "who is asking" for both authenticated shoppers and anonymous guests.
 * Anonymous requests carry a client-generated token so carts and assistant
 * conversations survive a page reload.
 */
@Component
public class ActorResolver {

    public static final String GUEST_TOKEN_HEADER = "X-Guest-Token";

    public ActorContext resolve(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof JwtAuthenticationFilter.Actor actor) {
            return new ActorContext(actor.userId(), actor.email(), actor.fullName(), actor.role(), null);
        }
        String guestToken = request.getHeader(GUEST_TOKEN_HEADER);
        if (guestToken == null || guestToken.isBlank()) {
            guestToken = request.getHeader("X-Cart-Token");
        }
        return new ActorContext(null, null, "Guest", "GUEST", normalize(guestToken));
    }

    private String normalize(String token) {
        if (token == null) {
            return null;
        }
        String trimmed = token.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() > 64 ? trimmed.substring(0, 64) : trimmed;
    }

    public boolean isStaff(ActorContext actor) {
        return "SUPPORT".equals(actor.role()) || "ADMIN".equals(actor.role());
    }

    public boolean isAuthenticated(ActorContext actor) {
        return actor.userId() != null;
    }

    /** Identity of the caller for the current request. */
    public record ActorContext(Long userId, String email, String fullName, String role, String guestToken) {

        public boolean authenticated() {
            return userId != null;
        }

        public boolean staff() {
            return "SUPPORT".equals(role) || "ADMIN".equals(role);
        }

        public String displayName() {
            return fullName == null || fullName.isBlank() ? "Guest" : fullName;
        }
    }
}
