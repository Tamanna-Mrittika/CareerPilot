package com.careerpilot.identity.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Identity has its own chain because, uniquely, it must expose endpoints that are reachable
 * <em>without</em> a token -- you cannot present an access token to the thing that issues
 * access tokens.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .cors(cors -> cors.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/v1/auth/register",
                                "/api/v1/auth/login",
                                "/api/v1/auth/refresh",
                                "/.well-known/**",
                                "/actuator/health/**",
                                "/actuator/prometheus",
                                "/actuator/info",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html").permitAll()
                        .anyRequest().authenticated())
                // Uses the locally defined JwtDecoder bean, so identity verifies its own tokens
                // with the in-process public key rather than fetching its own JWKS over HTTP.
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> {
                }));

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // Cost 12: noticeably slower than the default 10, which is the point -- it is the
        // main defence against offline cracking if the hash table ever leaks.
        return new BCryptPasswordEncoder(12);
    }
}
