package com.careerpilot.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

/**
 * Chooses the bucket each request is counted against.
 *
 * <p>Rate limiting per authenticated user rather than per IP matters here: university and
 * corporate networks put many users behind one NAT address, so an IP-only limit would let
 * one heavy user throttle an entire campus. IP is only the fallback for anonymous traffic
 * -- which is exactly where it belongs, since login and register are the endpoints worth
 * brute-forcing.
 *
 * <p>The token bucket itself lives in Redis, so the limit is shared across gateway
 * replicas instead of being per-instance.
 */
@Configuration
public class RateLimiterConfig {

    private static final String ANONYMOUS_PREFIX = "ip:";
    private static final String USER_PREFIX = "user:";

    @Bean
    @Primary
    public KeyResolver principalKeyResolver() {
        return exchange -> ReactiveSecurityContextHolder.getContext()
                .map(context -> context.getAuthentication())
                .filter(auth -> auth instanceof JwtAuthenticationToken)
                .map(auth -> USER_PREFIX + ((JwtAuthenticationToken) auth).getToken().getSubject())
                .switchIfEmpty(Mono.fromSupplier(() -> {
                    InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
                    return ANONYMOUS_PREFIX + (remote == null ? "unknown" : remote.getAddress().getHostAddress());
                }));
    }
}
