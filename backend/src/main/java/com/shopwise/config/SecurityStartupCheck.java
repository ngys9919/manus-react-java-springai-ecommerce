package com.shopwise.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Refuses to start with a signing key that is public knowledge.
 *
 * <p>The development default in {@code application.yml} is committed to the repository on
 * purpose, so a clone runs with no setup. That also means anyone can forge a token for a
 * deployment that forgets to override it. This check turns that silent, serious mistake into
 * a startup failure whenever a non-local profile is active.</p>
 *
 * <p>Local development is unaffected: the placeholder is allowed under the {@code dev},
 * {@code local}, {@code test} profiles, or when no profile is set at all.</p>
 */
@Component
public class SecurityStartupCheck {

    private static final Logger log = LoggerFactory.getLogger(SecurityStartupCheck.class);

    /** Must match the fallback in application.yml and ShopwiseProperties. */
    private static final String DEV_PLACEHOLDER =
            "shopwise-development-signing-key-change-me-please-32bytes";

    private static final List<String> LOCAL_PROFILES = List.of("dev", "local", "test", "it");

    /** HMAC-SHA256 needs at least 256 bits of key material. */
    private static final int MIN_SECRET_BYTES = 32;

    private final ShopwiseProperties properties;
    private final Environment environment;

    public SecurityStartupCheck(ShopwiseProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    @PostConstruct
    void verify() {
        String secret = properties.getSecurity().getJwtSecret();

        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "shopwise.security.jwt-secret is empty. Set SHOPWISE_JWT_SECRET to a random "
                            + "value of at least " + MIN_SECRET_BYTES + " bytes.");
        }

        int length = secret.getBytes(StandardCharsets.UTF_8).length;
        if (length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "shopwise.security.jwt-secret is only " + length + " bytes. HMAC-SHA256 requires at "
                            + "least " + MIN_SECRET_BYTES + ". Generate one with: openssl rand -base64 48");
        }

        boolean local = isLocalProfile();
        if (DEV_PLACEHOLDER.equals(secret)) {
            if (!local) {
                throw new IllegalStateException("""
                        Refusing to start: shopwise.security.jwt-secret is still the development \
                        placeholder, and the active profile is not a local one. \
                        Anyone with the repository could forge a signed-in session. \
                        Set SHOPWISE_JWT_SECRET to a random value, for example: openssl rand -base64 48""");
            }
            log.warn("Using the development JWT signing key. Set SHOPWISE_JWT_SECRET before deploying.");
            return;
        }

        if (!local && secret.toLowerCase(Locale.ROOT).contains("change-me")) {
            throw new IllegalStateException(
                    "shopwise.security.jwt-secret still looks like a placeholder. Set a real value.");
        }

        log.info("JWT signing key configured ({} bytes, non-default).", length);
    }

    private boolean isLocalProfile() {
        String[] active = environment.getActiveProfiles();
        if (active.length == 0) {
            return true;
        }
        return Arrays.stream(active)
                .map(profile -> profile.toLowerCase(Locale.ROOT))
                .anyMatch(LOCAL_PROFILES::contains);
    }
}
