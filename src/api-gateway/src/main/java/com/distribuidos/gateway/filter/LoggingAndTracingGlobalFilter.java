package com.distribuidos.gateway.filter;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Filtro global de trazabilidad: propaga {@code X-Correlation-Id} hacia los microservicios y
 * devuelve al cliente el mismo identificador junto con la latencia medida en el Gateway.
 *
 * <p>Las cabeceras de respuesta se registran con {@link ServerHttpResponse#beforeCommit}, que se
 * ejecuta justo antes de enviar el estado y las cabeceras al cliente. Escribirlas después de la
 * cadena de filtros no es válido: para entonces la respuesta ya está confirmada, sus cabeceras son
 * de solo lectura y la conexión se cierra sin completar el cuerpo.
 */
@Component
public class LoggingAndTracingGlobalFilter implements GlobalFilter, Ordered {

  private static final Logger log = LoggerFactory.getLogger(LoggingAndTracingGlobalFilter.class);

  public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
  public static final String RESPONSE_TIME_HEADER = "X-Response-Time-Ms";

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    long startNanos = System.nanoTime();
    String correlationId = resolveCorrelationId(exchange.getRequest().getHeaders());

    ServerHttpRequest request =
        exchange.getRequest().mutate().header(CORRELATION_ID_HEADER, correlationId).build();
    ServerWebExchange mutatedExchange = exchange.mutate().request(request).build();
    ServerHttpResponse response = mutatedExchange.getResponse();

    response.beforeCommit(
        () -> {
          HttpHeaders headers = response.getHeaders();
          headers.set(CORRELATION_ID_HEADER, correlationId);
          headers.set(RESPONSE_TIME_HEADER, String.valueOf(elapsedMillis(startNanos)));
          return Mono.empty();
        });

    log.info(
        "[GATEWAY START] TraceId: {} -> {} {}",
        correlationId,
        request.getMethod().name(),
        request.getURI().getPath());

    return chain
        .filter(mutatedExchange)
        .doFinally(
            signal ->
                log.info(
                    "[GATEWAY END] TraceId: {} -> Status: {} en {} ms ({})",
                    correlationId,
                    response.getStatusCode(),
                    elapsedMillis(startNanos),
                    signal));
  }

  @Override
  public int getOrder() {
    // Ejecución prioritaria al inicio de la cadena de filtros reactivos
    return Ordered.HIGHEST_PRECEDENCE;
  }

  private static String resolveCorrelationId(HttpHeaders headers) {
    String incoming = headers.getFirst(CORRELATION_ID_HEADER);
    return (incoming == null || incoming.isBlank()) ? UUID.randomUUID().toString() : incoming;
  }

  private static long elapsedMillis(long startNanos) {
    return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
  }
}
