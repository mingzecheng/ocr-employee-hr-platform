package com.hrplatform.gateway;

import com.hrplatform.common.api.TraceId;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

class TraceIdGlobalFilterTest {

    @Test
    void responseKeepsOnlyTheGatewayTraceIdWhenDownstreamAlreadyReturnedOne() {
        String traceId = "trace-from-client";
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/auth/me")
                        .header("X-Trace-Id", traceId)
                        .build());
        var filter = new TraceIdGlobalFilter();

        filter.filter(exchange, mutated -> {
            mutated.getResponse().getHeaders().add("X-Trace-Id", "trace-from-identity");
            return mutated.getResponse().setComplete();
        }).block();

        assertThat(exchange.getResponse().getHeaders().get("X-Trace-Id"))
                .containsExactly(traceId);
        assertThat(TraceId.normalize(exchange.getRequest().getHeaders().getFirst("X-Trace-Id")))
                .isEqualTo(traceId);
    }
}
