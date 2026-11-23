package com.distribuidos.gateway.filter;

import static com.distribuidos.gateway.filter.LoggingAndTracingGlobalFilter.CORRELATION_ID_HEADER;
import static com.distribuidos.gateway.filter.LoggingAndTracingGlobalFilter.RESPONSE_TIME_HEADER;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/** Pruebas del filtro global de trazabilidad (Correlation ID y latencia). */
class LoggingAndTracingGlobalFilterTest {

  private final LoggingAndTracingGlobalFilter filter = new LoggingAndTracingGlobalFilter();

  @Test
  void shouldAddTracingHeadersWhenDownstreamWritesBodyAndCommitsResponse() {
    MockServerWebExchange exchange =
        MockServerWebExchange.from(MockServerHttpRequest.get("/api/orders"));

    // Simula al microservicio respondiendo: escribir el cuerpo confirma (commit) la respuesta.
    GatewayFilterChain chain = downstreamWriting("[]");

    StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

    assertThat(exchange.getResponse().isCommitted()).isTrue();
    assertThat(exchange.getResponse().getHeaders().getFirst(CORRELATION_ID_HEADER)).isNotBlank();
    assertThat(exchange.getResponse().getHeaders().getFirst(RESPONSE_TIME_HEADER)).matches("\\d+");
    StepVerifier.create(exchange.getResponse().getBodyAsString()).expectNext("[]").verifyComplete();
  }

  @Test
  void shouldReuseIncomingCorrelationIdAndPropagateItDownstream() {
    MockServerWebExchange exchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/orders").header(CORRELATION_ID_HEADER, "trace-123"));
    AtomicReference<ServerHttpRequest> forwarded = new AtomicReference<>();

    GatewayFilterChain chain =
        mutated -> {
          forwarded.set(mutated.getRequest());
          return writeBody(mutated, "[]");
        };

    StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

    assertThat(forwarded.get().getHeaders().get(CORRELATION_ID_HEADER))
        .containsExactly("trace-123");
    assertThat(exchange.getResponse().getHeaders().get(CORRELATION_ID_HEADER))
        .containsExactly("trace-123");
  }

  @Test
  void shouldGenerateCorrelationIdWhenIncomingHeaderIsBlank() {
    MockServerWebExchange exchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/orders").header(CORRELATION_ID_HEADER, "  "));
    AtomicReference<ServerHttpRequest> forwarded = new AtomicReference<>();

    GatewayFilterChain chain =
        mutated -> {
          forwarded.set(mutated.getRequest());
          return writeBody(mutated, "[]");
        };

    StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

    String generated = forwarded.get().getHeaders().getFirst(CORRELATION_ID_HEADER);
    assertThat(generated).isNotBlank().isNotEqualTo("  ");
    assertThat(exchange.getResponse().getHeaders().getFirst(CORRELATION_ID_HEADER))
        .isEqualTo(generated);
  }

  @Test
  void shouldAddTracingHeadersWhenResponseHasNoBody() {
    MockServerWebExchange exchange =
        MockServerWebExchange.from(MockServerHttpRequest.get("/api/orders/999"));

    GatewayFilterChain chain =
        mutated -> {
          mutated.getResponse().setStatusCode(HttpStatus.NOT_FOUND);
          return mutated.getResponse().setComplete();
        };

    StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(exchange.getResponse().getHeaders().getFirst(CORRELATION_ID_HEADER)).isNotBlank();
    assertThat(exchange.getResponse().getHeaders().getFirst(RESPONSE_TIME_HEADER)).isNotBlank();
  }

  private static GatewayFilterChain downstreamWriting(String body) {
    return mutated -> writeBody(mutated, body);
  }

  private static Mono<Void> writeBody(ServerWebExchange exchange, String body) {
    DataBuffer buffer =
        exchange.getResponse().bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
    return exchange.getResponse().writeWith(Mono.just(buffer));
  }
}
