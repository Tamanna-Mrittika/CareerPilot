package com.careerpilot.identity.security;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

/**
 * Supplies the RSA signing key and the encoder/decoder built on it.
 *
 * <p>Key material is loaded from mounted PEM files. When none are configured the service
 * generates an ephemeral keypair so a fresh clone runs with zero setup -- but it says so
 * loudly, because that mode invalidates every previously issued token on each restart and
 * cannot work with more than one replica.
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
@Slf4j
public class JwkConfig {

    @Bean
    public RSAKey rsaKey(JwtProperties properties, ResourceLoader resourceLoader) {
        RSAPublicKey publicKey;
        RSAPrivateKey privateKey;

        if (StringUtils.hasText(properties.privateKeyLocation())
                && StringUtils.hasText(properties.publicKeyLocation())) {
            privateKey = loadPrivateKey(resourceLoader, properties.privateKeyLocation());
            publicKey = loadPublicKey(resourceLoader, properties.publicKeyLocation());
            log.info("Loaded RSA signing key from {}", properties.privateKeyLocation());
        } else {
            KeyPair keyPair = generateEphemeralKeyPair();
            publicKey = (RSAPublicKey) keyPair.getPublic();
            privateKey = (RSAPrivateKey) keyPair.getPrivate();
            log.warn("""
                    No JWT keypair configured -- generated an EPHEMERAL one.
                    Every token is invalidated on restart and multiple replicas will not agree.
                    Acceptable for local development only; mount real PEMs via \
                    careerpilot.jwt.private-key-location / public-key-location.""");
        }

        try {
            // Thumbprint as kid: stable for a given key, so rotation is observable by clients.
            RSAKey withoutId = new RSAKey.Builder(publicKey).privateKey(privateKey).build();
            return new RSAKey.Builder(withoutId)
                    .keyID(withoutId.computeThumbprint().toString())
                    .build();
        } catch (com.nimbusds.jose.JOSEException e) {
            throw new IllegalStateException("Unable to compute JWK thumbprint", e);
        }
    }

    @Bean
    public JWKSource<SecurityContext> jwkSource(RSAKey rsaKey) {
        return new ImmutableJWKSet<>(new JWKSet(rsaKey));
    }

    @Bean
    public JwtEncoder jwtEncoder(JWKSource<SecurityContext> jwkSource) {
        return new NimbusJwtEncoder(jwkSource);
    }

    /**
     * Lets this service validate its own tokens too, so its authenticated endpoints
     * (e.g. logout) behave exactly like every peer's.
     */
    @Bean
    public JwtDecoder jwtDecoder(RSAKey rsaKey) {
        try {
            return NimbusJwtDecoder.withPublicKey(rsaKey.toRSAPublicKey()).build();
        } catch (com.nimbusds.jose.JOSEException e) {
            throw new IllegalStateException("Unable to derive public key for decoding", e);
        }
    }

    private RSAPrivateKey loadPrivateKey(ResourceLoader loader, String location) {
        try (InputStream in = loader.getResource(location).getInputStream()) {
            return RsaKeyConverters.pkcs8().convert(in);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read JWT private key at " + location, e);
        }
    }

    private RSAPublicKey loadPublicKey(ResourceLoader loader, String location) {
        try (InputStream in = loader.getResource(location).getInputStream()) {
            return RsaKeyConverters.x509().convert(in);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read JWT public key at " + location, e);
        }
    }

    private KeyPair generateEphemeralKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("RSA unavailable in this JVM", e);
        }
    }
}
