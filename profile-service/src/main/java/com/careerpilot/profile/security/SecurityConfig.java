package com.careerpilot.profile.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Overrides {@code careerpilot-common}'s default resource-server chain to make the skill
 * taxonomy publicly readable.
 *
 * <p>The taxonomy is shared reference data, not user data: {@code resume-service} needs to
 * pull it on a background schedule (building its Aho-Corasick automaton) with no request
 * user in context, and the profile wizard's autocomplete has the same shape of need. Rather
 * than invent a service-to-service auth mechanism for one read-only, non-sensitive
 * resource, the taxonomy endpoints are simply public -- exactly like an actual job board
 * lets you browse its skill/category filters without logging in. Every other route here
 * (profiles, cover letters) stays authenticated exactly as {@code ResourceServerSecurityConfig}
 * defines by default.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private static final String[] PUBLIC_PATHS = {
            "/actuator/health/**",
            "/actuator/prometheus",
            "/actuator/info",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html"
    };

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter)
            throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .cors(cors -> cors.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        // GET only: the taxonomy has no write endpoints anyway, but being
                        // explicit means a future POST/PUT under this path does not
                        // accidentally inherit permitAll.
                        .requestMatchers(HttpMethod.GET, "/api/v1/skills/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)));

        return http.build();
    }
}
