package com.careerpilot.common.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Collection;
import java.util.List;

/**
 * Default resource-server posture for every business service.
 *
 * <p>Each service validates the JWT itself against the identity service's JWKS rather than
 * trusting that the gateway already did. That redundancy is intentional: if a
 * misconfiguration ever exposes a service port, or a compromised pod talks to a peer
 * directly, the token check still stands. The gateway is a policy enforcement point, not
 * the only one.
 *
 * <p>Services needing different rules can simply declare their own {@link SecurityFilterChain}
 * bean -- {@link ConditionalOnMissingBean} steps aside.
 */
@Configuration
@EnableMethodSecurity
public class ResourceServerSecurityConfig {

    /** Endpoints reachable without a token. Actuator is bound to the internal network only. */
    private static final String[] PUBLIC_PATHS = {
            "/actuator/health/**",
            "/actuator/prometheus",
            "/actuator/info",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html"
    };

    @Bean
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    public SecurityFilterChain defaultFilterChain(HttpSecurity http) throws Exception {
        http
                // No cookies, no sessions, no CSRF surface: the token is the whole auth story.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // CORS is handled once, at the gateway; services never answer browsers directly.
                .cors(cors -> cors.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));

        return http.build();
    }

    /** Maps our custom {@code roles} claim (["USER"]) onto Spring's ROLE_ authorities. */
    @Bean
    @ConditionalOnMissingBean
    public Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(ResourceServerSecurityConfig::extractAuthorities);
        return converter;
    }

    @SuppressWarnings("unchecked")
    private static Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
        Object claim = jwt.getClaims().get("roles");
        if (!(claim instanceof List<?> roles)) {
            return List.of();
        }
        return ((List<String>) roles).stream()
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
    }
}
