package com.shopwise.security;

import com.shopwise.config.ShopwiseProperties;
import com.shopwise.domain.AppUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {

    private final SecretKey key;
    private final ShopwiseProperties properties;

    public JwtService(ShopwiseProperties properties, SigningKey signingKey) {
        this.properties = properties;
        // SigningKey validates the configured secret as it is constructed, so an unset or
        // placeholder key fails here with a message that says what to do - rather than surfacing
        // later as an opaque "constructor threw exception".
        this.key = signingKey.key();
    }

    public String issue(AppUser user) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(properties.getSecurity().getTokenTtlMinutes() * 60);
        return Jwts.builder()
                .issuer(properties.getSecurity().getIssuer())
                .subject(String.valueOf(user.getId()))
                .claim("email", user.getEmail())
                .claim("name", user.getFullName())
                .claim("role", user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(key)
                .compact();
    }

    /** @return the parsed claims, or {@code null} when the token is missing, expired or forged. */
    public Claims parse(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(properties.getSecurity().getIssuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception ex) {
            return null;
        }
    }

    public long tokenTtlSeconds() {
        return properties.getSecurity().getTokenTtlMinutes() * 60;
    }
}
