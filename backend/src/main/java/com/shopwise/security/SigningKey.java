package com.shopwise.security;

import com.shopwise.config.ShopwiseProperties;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * The HMAC key used to sign sessions, validated before it can be used.
 *
 * <p>The development default in {@code application.yml} is committed to a public repository on
 * purpose, so a clone runs with no setup. That also means anyone can forge a token for a deployment
 * that forgets to override it. This component turns that silent, serious mistake into a startup
 * failure whenever a non-local profile is active.</p>
 *
 * <p>Validation lives here rather than in a separate startup check because bean creation order is
 * not guaranteed: a check declared elsewhere could run after {@code JwtService} had already failed
 * with an opaque "constructor threw exception". Anything that needs the key depends on this
 * component, so the clear message always wins the race.</p>
 */
@Component
public class SigningKey {

    private static final Logger log = LoggerFactory.getLogger(SigningKey.class);

    /** Must match the fallback in application.yml and ShopwiseProperties. */
    private static final String DEV_PLACEHOLDER =
            "shopwise-development-signing-key-change-me-please-32bytes";

    private static final List<String> LOCAL_PROFILES = List.of("dev", "local", "test", "it");

    /** HMAC-SHA256 needs at least 256 bits of key material. */
    private static final int MIN_SECRET_BYTES = 32;

    private final SecretKey key;

    public SigningKey(ShopwiseProperties properties, Environment environment) {
        String secret = properties.getSecurity().getJwtSecret();

        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "shopwise.security.jwt-secret is not set. Set SHOPWISE_JWT_SECRET to a random "
                            + "value of at least " + MIN_SECRET_BYTES + " bytes, for example: "
                            + "openssl rand -base64 48");
        }

        int length = secret.getBytes(StandardCharsets.UTF_8).length;
        if (length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "shopwise.security.jwt-secret is only " + length + " bytes. HMAC-SHA256 requires at "
                            + "least " + MIN_SECRET_BYTES + ". Generate one with: openssl rand -base64 48");
        }

        boolean local = isLocalProfile(environment);
        boolean placeholder = DEV_PLACEHOLDER.equals(secret)
                || secret.toLowerCase(Locale.ROOT).contains("change-me");

        if (placeholder && !local) {
            throw new IllegalStateException("""
                    Refusing to start: shopwise.security.jwt-secret is still the development \
                    placeholder, and the active profile is not a local one. Anyone with the \
                    repository could forge a signed-in session. Set SHOPWISE_JWT_SECRET to a random \
                    value, for example: openssl rand -base64 48""");
        }

        if (placeholder) {
            log.warn("Using the development JWT signing key. Set SHOPWISE_JWT_SECRET before deploying.");
        } else {
            log.info("JWT signing key configured ({} bytes, non-default).", length);
        }

        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public SecretKey key() {
        return key;
    }

    private static boolean isLocalProfile(Environment environment) {
        String[] active = environment.getActiveProfiles();
        if (active.length == 0) {
            return true;
        }
        return Arrays.stream(active)
                .map(profile -> profile.toLowerCase(Locale.ROOT))
                .anyMatch(LOCAL_PROFILES::contains);
    }
}
