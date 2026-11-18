package com.distribuidos.gateway.filter;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class LoggingAndTracingGlobalFilter implements GlobalFilter, Ordered {

  private static final Logger log = LoggerFactory.getLogger(LoggingAndTracingGlobalFilter.class);

  public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
  public static final String RESPONSE_TIME_HEADER = "X-Response-Time-Ms";
  private static final String START_TIME_ATTR = "startTimeMs";

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    long startTime = System.currentTimeMillis();
    exchange.getAttributes().put(START_TIME_ATTR, startTime);

    // 1. Extraer o generar X-Correlation-Id
    HttpHeaders headers = exchange.getRequest().getHeaders();
    String correlationId = headers.getFirst(CORRELATION_ID_HEADER);
    if (correlationId == null || correlationId.isBlank()) {
      correlationId = UUID.randomUUID().toString();
    }

    final String finalCorrelationId = correlationId;

    // Propagar Correlation ID hacia los microservicios downstream
    ServerWebExchange mutatedExchange =
        exchange
            .mutate()
            .request(
                exchange
                    .getRequest()
                    .mutate()
                    .header(CORRELATION_ID_HEADER, finalCorrelationId)
                    .build())
            .build();

    String path = exchange.getRequest().getURI().getPath();
    String method = exchange.getRequest().getMethod().name();

    log.info("[GATEWAY START] TraceId: {} -> {} {}", finalCorrelationId, method, path);

    // 2. Post-Filter: Calcular latencia e inyectar cabeceras en la respuesta al cliente
    return chain
        .filter(mutatedExchange)
        .then(
            Mono.fromRunnable(
                () -> {
                  Long start = mutatedExchange.getAttribute(START_TIME_ATTR);
                  long latency = (start != null) ? (System.currentTimeMillis() - start) : 0;

                  mutatedExchange
                      .getResponse()
                      .getHeaders()
                      .add(CORRELATION_ID_HEADER, finalCorrelationId);
                  mutatedExchange
                      .getResponse()
                      .getHeaders()
                      .add(RESPONSE_TIME_HEADER, String.valueOf(latency));

                  var statusCode = mutatedExchange.getResponse().getStatusCode();
                  log.info(
                      "[GATEWAY END] TraceId: {} -> Status: {} en {} ms",
                      finalCorrelationId,
                      statusCode,
                      latency);
                }));
  }

  @Override
  public int getOrder() {
    // Ejecución prioritaria al inicio de la cadena de filtros reactivos
    return Ordered.HIGHEST_PRECEDENCE;
  }
}
