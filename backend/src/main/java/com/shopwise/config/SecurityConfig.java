package com.shopwise.config;

import com.shopwise.security.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final ShopwiseProperties properties;

    public SecurityConfig(ShopwiseProperties properties) {
        this.properties = properties;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/api/v1/auth/**").permitAll()

                        // Public storefront
                        .requestMatchers(HttpMethod.GET, "/api/v1/catalog/**").permitAll()
                        // Guests may browse, build a cart and check out
                        .requestMatchers("/api/v1/cart/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/orders").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/orders/*/track").permitAll()
                        .requestMatchers("/api/v1/assistant/**").permitAll()

                        // Rails are public, but model health and placement click-through
                        // describe the whole business, so they sit behind staff roles.
                        .requestMatchers("/api/v1/recommendations/status",
                                         "/api/v1/recommendations/performance")
                            .hasAnyRole("SUPPORT", "ADMIN")
                        .requestMatchers("/api/v1/recommendations/**").permitAll()

                        // Personal data requires a session
                        .requestMatchers("/api/v1/me/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/orders/mine").authenticated()

                        // Assistant usage analytics + moderation console: staff only
                        .requestMatchers("/api/v1/insights/**").hasAnyRole("SUPPORT", "ADMIN")
                        .requestMatchers("/api/v1/support/**").hasAnyRole("SUPPORT", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/catalog/**").hasRole("ADMIN")

                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint((request, response, ex) -> {
                            response.setStatus(401);
                            response.setContentType("application/problem+json");
                            response.getWriter().write("""
                                    {"type":"about:blank","title":"Unauthorized","status":401,\
                                    "detail":"Sign in to access this resource.","code":"UNAUTHENTICATED"}""");
                        })
                        .accessDeniedHandler((request, response, ex) -> {
                            response.setStatus(403);
                            response.setContentType("application/problem+json");
                            response.getWriter().write("""
                                    {"type":"about:blank","title":"Forbidden","status":403,\
                                    "detail":"Your role does not grant access to this resource.","code":"FORBIDDEN"}""");
                        }))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        List<String> configured = new ArrayList<>(properties.getSecurity().getAllowedOrigins());
        boolean allowSameOrigin = properties.getSecurity().isAllowSameOrigin();

        return request -> {
            String origin = request.getHeader(HttpHeaders.ORIGIN);
            if (origin == null || origin.isBlank()) {
                // Not a cross-origin request, so there is nothing to negotiate.
                return null;
            }

            if (!configured.contains(origin) && !(allowSameOrigin && isSameOrigin(request, origin))) {
                // An empty configuration allows no origin, which Spring reports as
                // "Invalid CORS request" with a 403.
                return new CorsConfiguration();
            }

            CorsConfiguration configuration = new CorsConfiguration();
            configuration.setAllowedOrigins(List.of(origin));
            configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
            configuration.setAllowedHeaders(List.of("*"));
            configuration.setExposedHeaders(List.of("Location", "X-Cart-Token"));
            configuration.setAllowCredentials(true);
            configuration.setMaxAge(3600L);
            return configuration;
        };
    }

    /**
     * True when the browser's Origin is the very host it is talking to.
     *
     * <p>When the storefront and the API are served from one origin - which is how the Docker
     * deployment works, with nginx proxying {@code /api} - browsers still send an {@code Origin}
     * header on every mutating request. Rejecting those meant sign-in, cart and checkout all
     * returned 403 for any host that was not in a hardcoded list. A same-origin request needs no
     * CORS grant at all, so it is allowed here and the configured list is left to describe genuine
     * cross-origin callers such as the Vite dev server.</p>
     */
    private boolean isSameOrigin(HttpServletRequest request, String origin) {
        try {
            URI uri = URI.create(origin);
            String originAuthority = authority(uri.getHost(), uri.getPort(), uri.getScheme());
            if (originAuthority == null) {
                return false;
            }

            // The reverse proxy preserves the client's Host, port included, so prefer it over the
            // servlet's own view: nginx listens on a different port internally than the browser
            // connected to.
            String hostHeader = request.getHeader("X-Forwarded-Host");
            if (hostHeader == null || hostHeader.isBlank()) {
                hostHeader = request.getHeader(HttpHeaders.HOST);
            }

            String requestAuthority = hostHeader == null || hostHeader.isBlank()
                    ? authority(request.getServerName(), request.getServerPort(), request.getScheme())
                    : authorityFromHostHeader(hostHeader, uri.getScheme());

            return originAuthority.equalsIgnoreCase(requestAuthority);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    /** Parses a Host header, applying the scheme's default port when none is present. */
    private String authorityFromHostHeader(String hostHeader, String scheme) {
        String value = hostHeader.trim();

        // A bracketed IPv6 literal, optionally followed by :port.
        if (value.startsWith("[")) {
            int closing = value.indexOf(']');
            if (closing < 0) {
                return null;
            }
            int port = closing + 1 < value.length() && value.charAt(closing + 1) == ':'
                    ? parsePort(value.substring(closing + 2))
                    : -1;
            return authority(value.substring(1, closing), port, scheme);
        }

        int colon = value.lastIndexOf(':');
        if (colon >= 0 && value.indexOf(':') == colon) {
            return authority(value.substring(0, colon), parsePort(value.substring(colon + 1)), scheme);
        }
        return authority(value, -1, scheme);
    }

    private int parsePort(String raw) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    /** Normalises to host:port, treating the scheme's default port as absent. */
    private String authority(String host, int port, String scheme) {
        if (host == null) {
            return null;
        }
        String normalisedHost = host.toLowerCase(Locale.ROOT);
        int resolvedPort = port;
        if (resolvedPort < 0) {
            resolvedPort = "https".equalsIgnoreCase(scheme) ? 443 : 80;
        }
        return normalisedHost + ":" + resolvedPort;
    }
}
