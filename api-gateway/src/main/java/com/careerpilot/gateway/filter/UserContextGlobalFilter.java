package com.careerpilot.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Publishes the authenticated user id downstream as {@code X-User-Id}, for logging and
 * cheap request attribution.
 *
 * <p>The security-critical half of this filter is the <em>removal</em> step: any
 * {@code X-User-Id} a client sends is stripped unconditionally before the header is
 * re-added from the verified token. Without that, anyone could impersonate another user
 * by setting a header, since downstream services see this value as trusted context.
 *
 * <p>Even so, services authorise on the JWT subject rather than on this header
 * (see {@code CurrentUser}). The header is convenience; the token is the authority.
 */
@Component
public class UserContextGlobalFilter implements GlobalFilter, Ordered {

    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String USER_EMAIL_HEADER = "X-User-Email";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Strip first, always -- including on anonymous routes such as /auth/login.
        ServerHttpRequest sanitised = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.remove(USER_ID_HEADER);
                    headers.remove(USER_EMAIL_HEADER);
                })
                .build();

        ServerWebExchange sanitisedExchange = exchange.mutate().request(sanitised).build();

        return ReactiveSecurityContextHolder.getContext()
                .map(context -> context.getAuthentication())
                .filter(auth -> auth instanceof JwtAuthenticationToken)
                .map(auth -> ((JwtAuthenticationToken) auth).getToken())
                .map(jwt -> withUserHeaders(sanitisedExchange, jwt))
                .defaultIfEmpty(sanitisedExchange)
                .flatMap(chain::filter);
    }

    private ServerWebExchange withUserHeaders(ServerWebExchange exchange, Jwt jwt) {
        ServerHttpRequest request = exchange.getRequest().mutate()
                .header(USER_ID_HEADER, jwt.getSubject())
                .header(USER_EMAIL_HEADER, jwt.getClaimAsString("email"))
                .build();
        return exchange.mutate().request(request).build();
    }

    @Override
    public int getOrder() {
        // After the security filters have populated the reactive security context.
        return Ordered.LOWEST_PRECEDENCE - 100;
    }
}
