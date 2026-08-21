package com.careerpilot.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Edge authentication. Tokens are verified against identity-service's JWKS, which the
 * gateway fetches and caches on first use -- no shared secret is configured anywhere.
 */
@Configuration
public class GatewaySecurityConfig {

    /** Routes that must work before a user has a token. */
    private static final String[] PUBLIC_PATHS = {
            "/api/v1/auth/register",
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            // Machine-to-machine: a mail provider cannot present a JWT. Authenticated
            // downstream by HMAC signature over the raw body, which fails closed, so this
            // is not an unauthenticated hole -- just a different authentication layer.
            "/api/v1/webhooks/email",
            "/actuator/health/**",
            "/actuator/prometheus",
            "/actuator/info",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/webjars/**"
    };

    @Value("${careerpilot.cors.allowed-origins}")
    private List<String> allowedOrigins;

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .authorizeExchange(exchange -> exchange
                        // Preflights carry no credentials by definition; blocking them just
                        // breaks the browser before the real request is ever attempted.
                        .pathMatchers(HttpMethod.OPTIONS).permitAll()
                        .pathMatchers(PUBLIC_PATHS).permitAll()
                        // job-service's own SecurityConfig deliberately permits anonymous GET
                        // browsing (a job board should not require login to search postings),
                        // but that intent never reached the gateway: without this matcher the
                        // edge rejected every unauthenticated job request before job-service
                        // was ever asked, silently breaking the feature end to end. Scoped to
                        // GET only so POST /api/v1/jobs/ingest still falls through to
                        // anyExchange().authenticated() plus its ADMIN-only @PreAuthorize.
                        .pathMatchers(HttpMethod.GET, "/api/v1/jobs/**").permitAll()
                        .anyExchange().authenticated())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> {
                }));

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        // Explicit origin list, never "*": allowCredentials plus a wildcard origin is
        // rejected by browsers anyway, and a wildcard here would be a real hole.
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Correlation-Id", "Idempotency-Key"));
        config.setExposedHeaders(List.of("X-Correlation-Id", "Location", "Retry-After"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
