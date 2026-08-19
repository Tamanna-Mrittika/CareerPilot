package com.careerpilot.gateway.filter;

import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Mints the correlation ID at the edge and forwards it downstream.
 *
 * <p>Reactive equivalent of the servlet filter in {@code careerpilot-common}. Note the MDC
 * handling: in WebFlux a request is not pinned to one thread, so the MDC value is set for
 * the synchronous portion of this filter only and cleared immediately. The header, not the
 * MDC, is what actually carries the ID across the system.
 */
@Component
public class CorrelationIdGlobalFilter implements GlobalFilter, Ordered {

    public static final String HEADER = "X-Correlation-Id";
    private static final String MDC_KEY = "correlationId";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String correlationId = exchange.getRequest().getHeaders().getFirst(HEADER);
        if (!StringUtils.hasText(correlationId)) {
            correlationId = UUID.randomUUID().toString();
        }
        final String resolved = correlationId;

        ServerHttpRequest mutated = exchange.getRequest().mutate()
                .header(HEADER, resolved)
                .build();

        // Written at commit time, not now. Downstream services set this header too, and the
        // proxied response headers are merged over ours -- setting it here yields a
        // duplicated "id,id" value. beforeCommit runs after that merge, so set() genuinely
        // replaces rather than appends.
        exchange.getResponse().beforeCommit(() -> {
            exchange.getResponse().getHeaders().set(HEADER, resolved);
            return Mono.empty();
        });

        MDC.put(MDC_KEY, resolved);
        try {
            return chain.filter(exchange.mutate().request(mutated).build());
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    @Override
    public int getOrder() {
        // Ahead of routing and security so failures in either are already correlated.
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
