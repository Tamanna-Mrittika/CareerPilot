package com.careerpilot.identity;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Issues the RS256 access tokens that every other service trusts, and publishes the public
 * half of the signing key at {@code /.well-known/jwks.json}.
 *
 * <p>This is the only service holding the private key. Peers verify signatures with the
 * public key alone, so compromising a downstream service does not let an attacker mint
 * tokens -- which is the whole reason we chose asymmetric RS256 over a shared HS256 secret.
 */
@SpringBootApplication
public class IdentityServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(IdentityServiceApplication.class, args);
    }
}
