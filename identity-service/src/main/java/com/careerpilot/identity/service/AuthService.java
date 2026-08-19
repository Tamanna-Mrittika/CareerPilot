package com.careerpilot.identity.service;

import com.careerpilot.common.error.ApiException;
import com.careerpilot.common.error.ConflictException;
import com.careerpilot.identity.api.dto.AuthDtos.AuthResponse;
import com.careerpilot.identity.api.dto.AuthDtos.LoginRequest;
import com.careerpilot.identity.api.dto.AuthDtos.RegisterRequest;
import com.careerpilot.identity.api.dto.AuthDtos.UserSummary;
import com.careerpilot.identity.domain.RefreshToken;
import com.careerpilot.identity.domain.Role;
import com.careerpilot.identity.domain.UserAccount;
import com.careerpilot.identity.repository.UserAccountRepository;
import com.careerpilot.identity.security.TokenIssuer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserAccountRepository users;
    private final RefreshTokenService refreshTokens;
    private final TokenIssuer tokenIssuer;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = request.email().toLowerCase().trim();
        if (users.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("An account with that email already exists");
        }

        UserAccount user = users.save(
                UserAccount.create(email, passwordEncoder.encode(request.password()), request.fullName().trim()));

        log.info("Registered user {}", user.getId());
        return buildResponse(user, refreshTokens.issueNewFamily(user.getId()));
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        Optional<UserAccount> found = users.findByEmailIgnoreCase(request.email().trim());

        // Verify a hash even when the account does not exist. Skipping the BCrypt work for
        // unknown emails would make "no such user" measurably faster than "wrong password",
        // turning response time into a user-enumeration oracle.
        String hashToCheck = found.map(UserAccount::getPasswordHash).orElse(DUMMY_HASH);
        boolean passwordMatches = passwordEncoder.matches(request.password(), hashToCheck);

        if (found.isEmpty() || !passwordMatches) {
            throw new InvalidCredentialsException();
        }

        UserAccount user = found.get();
        if (!user.isEnabled()) {
            throw new AccountDisabledException();
        }

        return buildResponse(user, refreshTokens.issueNewFamily(user.getId()));
    }

    @Transactional
    public AuthResponse refresh(String rawRefreshToken) {
        RefreshToken consumed = refreshTokens.consume(rawRefreshToken)
                .orElseThrow(InvalidRefreshTokenException::new);

        UserAccount user = users.findById(consumed.getUserId())
                .orElseThrow(InvalidRefreshTokenException::new);

        if (!user.isEnabled()) {
            refreshTokens.revokeAllForUser(user.getId());
            throw new AccountDisabledException();
        }

        // Stay in the same family so reuse detection can still see the lineage.
        String rotated = refreshTokens.issueWithinFamily(user.getId(), consumed.getFamilyId());
        return buildResponse(user, rotated);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        // Best effort: an unknown or already-consumed token still yields 204, so logout is
        // idempotent and never leaks whether the token was valid.
        refreshTokens.consume(rawRefreshToken)
                .ifPresent(token -> refreshTokens.revokeFamily(token.getFamilyId()));
    }

    @Transactional(readOnly = true)
    public UserSummary currentUser(UUID userId) {
        return users.findById(userId)
                .map(AuthService::toSummary)
                .orElseThrow(InvalidCredentialsException::new);
    }

    private AuthResponse buildResponse(UserAccount user, String refreshToken) {
        TokenIssuer.IssuedAccessToken accessToken = tokenIssuer.issue(user);
        return AuthResponse.of(accessToken.value(), refreshToken,
                accessToken.expiresInSeconds(), toSummary(user));
    }

    private static UserSummary toSummary(UserAccount user) {
        return new UserSummary(user.getId(), user.getEmail(), user.getFullName(),
                user.getRoles().stream().map(Role::name).toList());
    }

    /**
     * A real BCrypt hash of a value nobody can supply, used purely to burn the same CPU
     * time as a genuine verification. Must stay a valid BCrypt string or matches() would
     * return early and defeat the purpose.
     */
    private static final String DUMMY_HASH =
            "$2a$12$C6UzMDM.H6dfI/f/IKcEe.wXKQZ1DPCsAmvS9O8DPa1kR7cLqrJ2m";

    private static class InvalidCredentialsException extends ApiException {
        InvalidCredentialsException() {
            // One message for both failure modes, again to avoid confirming which emails exist.
            super(HttpStatus.UNAUTHORIZED, "Authentication failed", "Invalid email or password");
        }
    }

    private static class InvalidRefreshTokenException extends ApiException {
        InvalidRefreshTokenException() {
            super(HttpStatus.UNAUTHORIZED, "Authentication failed",
                    "Refresh token is invalid, expired, or has already been used");
        }
    }

    private static class AccountDisabledException extends ApiException {
        AccountDisabledException() {
            super(HttpStatus.FORBIDDEN, "Account disabled", "This account has been disabled");
        }
    }
}
