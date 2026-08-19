package com.careerpilot.identity.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/** Request/response payloads for the auth API, grouped so the contract reads in one place. */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Email @Size(max = 254) String email,
            // 12 is a deliberate floor: length dominates password strength far more than
            // character-class rules, which mostly teach users to write "Password1!".
            @NotBlank @Size(min = 12, max = 128,
                    message = "Password must be at least 12 characters") String password,
            @NotBlank @Size(max = 120) String fullName) {
    }

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password) {
    }

    public record RefreshRequest(
            @NotBlank String refreshToken) {
    }

    public record AuthResponse(
            String accessToken,
            String refreshToken,
            String tokenType,
            long expiresIn,
            UserSummary user) {

        public static AuthResponse of(String accessToken, String refreshToken,
                                      long expiresIn, UserSummary user) {
            return new AuthResponse(accessToken, refreshToken, "Bearer", expiresIn, user);
        }
    }

    public record UserSummary(
            UUID id,
            String email,
            String fullName,
            List<String> roles) {
    }
}
