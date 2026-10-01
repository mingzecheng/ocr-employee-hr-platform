package com.hrplatform.gateway;

import com.hrplatform.common.api.TraceId;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class TraceIdGlobalFilter implements GlobalFilter, Ordered {

    private static final String TRACE_HEADER = "X-Trace-Id";
    private static final String[] INTERNAL_HEADERS = {
            "X-Internal-User", "X-Internal-Roles", "X-Internal-Permissions"
    };

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String traceId = TraceId.normalize(exchange.getRequest().getHeaders().getFirst(TRACE_HEADER));
        if (traceId == null) {
            traceId = TraceId.generate();
        }
        final String resolvedTraceId = traceId;
        ServerHttpRequest.Builder request = exchange.getRequest().mutate().header(TRACE_HEADER, traceId);
        for (String header : INTERNAL_HEADERS) {
            request.headers(headers -> headers.remove(header));
        }
        ServerWebExchange mutated = exchange.mutate().request(request.build()).build();
        mutated.getResponse().getHeaders().remove(TRACE_HEADER);
        mutated.getResponse().getHeaders().set(TRACE_HEADER, traceId);
        mutated.getResponse().beforeCommit(() -> {
            mutated.getResponse().getHeaders().remove(TRACE_HEADER);
            mutated.getResponse().getHeaders().set(TRACE_HEADER, resolvedTraceId);
            return Mono.empty();
        });
        return chain.filter(mutated);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
