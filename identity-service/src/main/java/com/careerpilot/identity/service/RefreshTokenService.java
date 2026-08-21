package com.careerpilot.identity.service;

import com.careerpilot.identity.domain.RefreshToken;
import com.careerpilot.identity.repository.RefreshTokenRepository;
import com.careerpilot.identity.security.JwtProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/**
 * Issues, rotates and revokes opaque refresh tokens.
 *
 * <p>Two properties matter here. First, only hashes are persisted, so the database never
 * contains a usable credential. Second, rotation is mandatory: each refresh consumes the
 * presented token and returns a new one. Presenting a consumed token is therefore
 * evidence of theft or replay, and we respond by revoking the entire token family rather
 * than the single token -- otherwise an attacker who stole a token could keep refreshing
 * alongside the real user indefinitely.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenRepository repository;
    private final JwtProperties properties;
    private final TokenFamilyRevoker familyRevoker;

    /** Starts a brand-new family (a fresh login). */
    @Transactional
    public String issueNewFamily(UUID userId) {
        return issueWithinFamily(userId, UUID.randomUUID());
    }

    @Transactional
    public String issueWithinFamily(UUID userId, UUID familyId) {
        String rawToken = randomToken();
        Instant expiresAt = Instant.now().plus(properties.refreshTokenTtl());
        repository.save(RefreshToken.issue(userId, hash(rawToken), familyId, expiresAt));
        // The raw value is returned exactly once and never stored anywhere.
        return rawToken;
    }

    /**
     * Validates and consumes a presented token.
     *
     * @return the consumed record when it was genuinely usable, otherwise empty
     */
    @Transactional
    public Optional<RefreshToken> consume(String rawToken) {
        Optional<RefreshToken> found = repository.findByTokenHash(hash(rawToken));
        if (found.isEmpty()) {
            return Optional.empty();
        }

        RefreshToken token = found.get();

        if (token.isRevoked()) {
            // Already-rotated token replayed: assume compromise and cut off the whole family.
            log.warn("Refresh token reuse detected for user {} (family {}); revoking family",
                    token.getUserId(), token.getFamilyId());
            // Through the TokenFamilyRevoker bean, not a local method -- see its javadoc:
            // a same-class call here would bypass the transactional proxy and this revoke
            // would be silently rolled back along with everything else once the caller
            // throws to report the failed replay.
            familyRevoker.revokeIndependently(token.getFamilyId());
            return Optional.empty();
        }

        if (token.isExpired()) {
            return Optional.empty();
        }

        token.revoke();
        repository.save(token);
        return Optional.of(token);
    }

    @Transactional
    public void revokeFamily(UUID familyId) {
        repository.revokeFamily(familyId, Instant.now());
    }

    @Transactional
    public void revokeAllForUser(UUID userId) {
        repository.revokeAllForUser(userId, Instant.now());
    }

    private String randomToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Plain SHA-256, intentionally not BCrypt. The token is 256 bits of cryptographic
     * randomness, so it has no low-entropy guess space for an attacker to search; the
     * slow-hash property that protects human-chosen passwords would buy nothing and would
     * make every refresh measurably slower.
     */
    private String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable in this JVM", e);
        }
    }
}
