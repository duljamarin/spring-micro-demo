package com.demo.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Stamps every request that enters through the gateway with a correlation id
 * (if the caller didn't already supply one) so a single business transaction
 * can be traced across service boundaries in each service's logs.
 */
@Component
public class CorrelationIdFilter implements GlobalFilter, Ordered {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        if (request.getHeaders().getFirst(CORRELATION_ID_HEADER) == null) {
            String correlationId = UUID.randomUUID().toString();
            ServerHttpRequest mutated = request.mutate()
                    .header(CORRELATION_ID_HEADER, correlationId)
                    .build();
            return chain.filter(exchange.mutate().request(mutated).build());
        }
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
