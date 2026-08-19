package com.careerpilot.identity.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * @param issuer               {@code iss} claim; peers reject tokens from anyone else.
 * @param audience             {@code aud} claim identifying this system's APIs.
 * @param accessTokenTtl       Short by design -- a stolen access token cannot be revoked,
 *                             so its blast radius is bounded by expiry alone.
 * @param refreshTokenTtl      Long-lived but revocable, because it lives in the database.
 * @param privateKeyLocation   Spring resource (e.g. {@code file:/run/secrets/jwt-private.pem}).
 *                             Blank generates an ephemeral dev keypair.
 * @param publicKeyLocation    Matching public key.
 */
@ConfigurationProperties(prefix = "careerpilot.jwt")
public record JwtProperties(
        String issuer,
        String audience,
        Duration accessTokenTtl,
        Duration refreshTokenTtl,
        String privateKeyLocation,
        String publicKeyLocation
) {
    public JwtProperties {
        if (accessTokenTtl == null) {
            accessTokenTtl = Duration.ofMinutes(15);
        }
        if (refreshTokenTtl == null) {
            refreshTokenTtl = Duration.ofDays(14);
        }
    }
}
