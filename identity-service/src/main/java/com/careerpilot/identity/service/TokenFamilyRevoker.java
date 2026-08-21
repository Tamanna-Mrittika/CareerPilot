package com.careerpilot.identity.service;

import com.careerpilot.identity.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Revokes a refresh-token family in its own, independent transaction.
 *
 * <p>This is a separate bean from {@link RefreshTokenService} for the same reason
 * {@code JobPersistenceService} and {@code ResumeProcessingService} are split out
 * elsewhere in this codebase: Spring's {@code @Transactional} is proxy-based, so a method
 * called from another method on the <em>same</em> bean bypasses the proxy and
 * {@code REQUIRES_NEW} silently does nothing -- the call just joins whatever transaction
 * was already open. That is exactly what happened here: {@code consume()}'s reuse-detected
 * branch calls this revocation, then the caller ({@code AuthService.refresh()}) throws to
 * report the failed replay, and Spring's default rollback-on-unchecked-exception rule
 * undid the revocation along with everything else in that transaction -- confirmed live by
 * inspecting the database after a same-class self-invocation attempt still left the
 * family's other token unrevoked. Going through this bean's own proxy is what actually
 * gives the revocation its own transaction, so it survives the caller's subsequent
 * rollback.
 */
@Service
@RequiredArgsConstructor
public class TokenFamilyRevoker {

    private final RefreshTokenRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revokeIndependently(UUID familyId) {
        repository.revokeFamily(familyId, Instant.now());
    }
}
