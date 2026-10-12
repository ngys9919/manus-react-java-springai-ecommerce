package com.shopwise.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            Claims claims = jwtService.parse(header.substring(7).trim());
            if (claims != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                String role = String.valueOf(claims.get("role"));
                Actor actor = new Actor(
                        Long.valueOf(claims.getSubject()),
                        String.valueOf(claims.get("email")),
                        String.valueOf(claims.get("name")),
                        role);
                var authentication = new UsernamePasswordAuthenticationToken(
                        actor, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
                authentication.setDetails(claims);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }
        filterChain.doFilter(request, response);
    }

    /** Authenticated principal carried on the security context. */
    public record Actor(Long userId, String email, String fullName, String role) {
    }
}
