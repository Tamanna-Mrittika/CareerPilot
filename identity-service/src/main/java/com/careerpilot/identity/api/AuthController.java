package com.careerpilot.identity.api;

import com.careerpilot.common.security.CurrentUser;
import com.careerpilot.identity.api.dto.AuthDtos.AuthResponse;
import com.careerpilot.identity.api.dto.AuthDtos.LoginRequest;
import com.careerpilot.identity.api.dto.AuthDtos.RefreshRequest;
import com.careerpilot.identity.api.dto.AuthDtos.RegisterRequest;
import com.careerpilot.identity.api.dto.AuthDtos.UserSummary;
import com.careerpilot.identity.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @Operation(summary = "Create an account and return an initial token pair")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    @Operation(summary = "Exchange credentials for an access + refresh token pair")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotate a refresh token for a new token pair",
            description = "The presented refresh token is consumed. Replaying it revokes the whole token family.")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    @Operation(summary = "Revoke the refresh token family behind this session")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    @Operation(summary = "Return the authenticated user, derived from the validated token")
    public UserSummary me() {
        return authService.currentUser(CurrentUser.requireId());
    }
}
