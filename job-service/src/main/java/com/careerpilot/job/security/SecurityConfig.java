package com.careerpilot.job.security;

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
 * Overrides {@code careerpilot-common}'s default resource-server chain to make job
 * <em>browsing</em> public, while keeping ingestion locked down.
 *
 * <p>Two independent reasons converge on the same answer here. Product-wise, requiring
 * login to search job postings is not how a job board should work -- browsing is the
 * point of the product. Architecturally, {@code resume-service} needs to read job
 * descriptions (a specific job for on-demand ATS scoring, and a sampled page of
 * descriptions on a background schedule to build its IDF corpus) with no request user in
 * context, so the read side has to be reachable without a token regardless.
 *
 * <p>{@code POST /api/v1/jobs/ingest} is deliberately <em>not</em> in the public path list
 * -- explicit {@code HttpMethod.GET} scoping means that route falls through to
 * {@code anyRequest().authenticated()} and still requires the {@code @PreAuthorize
 * hasRole('ADMIN')} on {@code JobController.ingest()}, because each call spends real,
 * finite external API quota/money.
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
                        .requestMatchers(HttpMethod.GET, "/api/v1/jobs/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)));

        return http.build();
    }
}
