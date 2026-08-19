package com.careerpilot.identity.security;

import com.careerpilot.identity.domain.Role;
import com.careerpilot.identity.domain.UserAccount;
import com.nimbusds.jose.jwk.RSAKey;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Mints signed RS256 access tokens. Refresh tokens are opaque and handled separately. */
@Component
public class TokenIssuer {

    private final JwtEncoder encoder;
    private final JwtProperties properties;
    private final String keyId;

    public TokenIssuer(JwtEncoder encoder, JwtProperties properties, RSAKey rsaKey) {
        this.encoder = encoder;
        this.properties = properties;
        this.keyId = rsaKey.getKeyID();
    }

    public IssuedAccessToken issue(UserAccount user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(properties.accessTokenTtl());

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .audience(List.of(properties.audience()))
                // Subject is the user id, never the email: emails can change, ids cannot.
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .id(UUID.randomUUID().toString())
                .claim("email", user.getEmail())
                .claim("name", user.getFullName())
                .claim("roles", user.getRoles().stream().map(Role::name).toList())
                .build();

        // kid lets peers pick the right key from the JWKS, which is what makes rotation possible.
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(keyId).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();

        return new IssuedAccessToken(token, expiresAt, properties.accessTokenTtl().toSeconds());
    }

    public record IssuedAccessToken(String value, Instant expiresAt, long expiresInSeconds) {
    }
}
