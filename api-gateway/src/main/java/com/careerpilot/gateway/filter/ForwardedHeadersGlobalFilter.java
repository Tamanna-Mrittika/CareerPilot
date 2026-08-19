package com.careerpilot.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Stamps the original, externally-visible host onto every proxied request as
 * {@code X-Forwarded-Host}/{@code X-Forwarded-Proto}.
 *
 * <p>This exists because this Spring Cloud Gateway build (the
 * {@code spring-cloud-starter-gateway-server-webflux} artifact, Spring Cloud 2025.0.0)
 * does <strong>not</strong> add these headers itself -- verified empirically: a direct
 * header dump on a downstream service showed no {@code X-Forwarded-*} headers at all, and
 * the plain {@code Host} header had already been rewritten to the downstream service's own
 * internal Docker address (e.g. {@code 172.18.0.10:8083}) by the time it arrived. Any
 * service that needs to build an externally-reachable absolute URL -- resume-service's
 * upload endpoint returning a {@code Location} header for its 202 response is the first
 * case -- has no way to recover the real gateway URL without this.
 *
 * <p>Runs at default {@code GlobalFilter} precedence, before the gateway's own routing
 * filter reconstructs the outbound request, so {@link ServerWebExchange#getRequest()} here
 * still reflects the original incoming request exactly as the client sent it.
 */
@Component
public class ForwardedHeadersGlobalFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest original = exchange.getRequest();

        String host = original.getHeaders().getFirst(HttpHeaders.HOST);
        if (!StringUtils.hasText(host)) {
            // No Host header at all is not realistic for a browser/API client, but do not
            // block the request over it -- just skip stamping and let the fallback in each
            // service's own code (raw request host/port) apply instead.
            return chain.filter(exchange);
        }

        String scheme = original.getURI().getScheme();
        if (!StringUtils.hasText(scheme)) {
            scheme = "http";
        }

        ServerHttpRequest mutated = original.mutate()
                .header("X-Forwarded-Host", host)
                .header("X-Forwarded-Proto", scheme)
                .build();

        return chain.filter(exchange.mutate().request(mutated).build());
    }

    @Override
    public int getOrder() {
        // Ahead of UserContextGlobalFilter/routing, alongside CorrelationIdGlobalFilter --
        // this is edge metadata every downstream call should carry, not something tied to
        // a specific route's business logic.
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }
}
