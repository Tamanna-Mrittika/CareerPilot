package com.careerpilot.common.security;

import com.careerpilot.common.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads the authenticated principal out of the validated JWT.
 *
 * <p>Note what this deliberately does <em>not</em> do: it never trusts a user id supplied
 * in a request body or path variable. Identity comes only from a cryptographically
 * verified token, so one user cannot read another's resume by editing a URL. The
 * {@code X-User-Id} header the gateway injects is a convenience for logging, never an
 * authorisation input.
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    /** @throws ApiException 401 when the request is unauthenticated. */
    public static UUID requireId() {
        return id().orElseThrow(() -> new UnauthenticatedException("No authenticated user in security context"));
    }

    public static Optional<UUID> id() {
        return jwt().map(Jwt::getSubject).map(UUID::fromString);
    }

    public static Optional<String> email() {
        return jwt().map(j -> j.getClaimAsString("email"));
    }

    /** Display name from the token's {@code name} claim, set at registration. */
    public static Optional<String> name() {
        return jwt().map(j -> j.getClaimAsString("name"));
    }

    @SuppressWarnings("unchecked")
    public static List<String> roles() {
        return jwt().map(j -> (List<String>) j.getClaims().getOrDefault("roles", List.of()))
                .orElseGet(List::of);
    }

    /**
     * The raw compact JWS string of the caller's own token, for services that must call a
     * peer's <em>private</em>, user-scoped endpoint on the caller's behalf -- e.g.
     * matching-service reading the caller's own profile from profile-service's
     * {@code GET /api/v1/profiles/me}. Deliberately distinct from the public,
     * no-auth-needed reads other services use (skill taxonomy, job listings): forwarding
     * the caller's real token, rather than inventing a service credential, means the
     * downstream call is authorised as the same user and carries no more trust than the
     * original request already had.
     */
    public static Optional<String> rawToken() {
        return jwt().map(Jwt::getTokenValue);
    }

    private static Optional<Jwt> jwt() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt token) {
            return Optional.of(token);
        }
        return Optional.empty();
    }

    private static class UnauthenticatedException extends ApiException {
        UnauthenticatedException(String detail) {
            super(HttpStatus.UNAUTHORIZED, "Unauthenticated", detail);
        }
    }
}
