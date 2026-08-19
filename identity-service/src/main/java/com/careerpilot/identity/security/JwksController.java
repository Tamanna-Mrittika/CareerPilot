package com.careerpilot.identity.security;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Publishes the public signing key so every other service can verify tokens without ever
 * holding a secret. This endpoint is deliberately unauthenticated -- a public key is public.
 */
@RestController
public class JwksController {

    private final JWKSet publicJwkSet;

    public JwksController(RSAKey rsaKey) {
        // toPublicJWK() strips the private exponent. Getting this wrong would publish the
        // signing key to the network, so it is the single most important line in the class.
        this.publicJwkSet = new JWKSet(rsaKey.toPublicJWK());
    }

    @GetMapping("/.well-known/jwks.json")
    @Operation(summary = "JSON Web Key Set used by all services to verify access tokens")
    public Map<String, Object> jwks() {
        return publicJwkSet.toJSONObject();
    }
}
