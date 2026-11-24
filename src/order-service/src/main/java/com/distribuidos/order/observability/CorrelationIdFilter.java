package com.distribuidos.order.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Coloca el {@code X-Correlation-Id} de la petición y la instancia en el contexto de log (MDC).
 *
 * <p>El Gateway genera el identificador; si la petición llega directa, se genera uno aquí. Se
 * devuelve en la respuesta y se limpia del MDC al terminar, porque Tomcat reutiliza los hilos.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

  private final InstanceIdentity instanceIdentity;

  /**
   * Crea el filtro.
   *
   * @param instanceIdentity identidad de esta instancia.
   */
  public CorrelationIdFilter(InstanceIdentity instanceIdentity) {
    this.instanceIdentity = instanceIdentity;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String correlationId = resolveCorrelationId(request);
    MDC.put(MdcKeys.CORRELATION_ID, correlationId);
    MDC.put(MdcKeys.INSTANCE_ID, instanceIdentity.id());
    response.setHeader(MdcKeys.CORRELATION_ID_HEADER, correlationId);
    try {
      chain.doFilter(request, response);
    } finally {
      MDC.remove(MdcKeys.CORRELATION_ID);
      MDC.remove(MdcKeys.INSTANCE_ID);
    }
  }

  private static String resolveCorrelationId(HttpServletRequest request) {
    String incoming = request.getHeader(MdcKeys.CORRELATION_ID_HEADER);
    return (incoming == null || incoming.isBlank()) ? UUID.randomUUID().toString() : incoming;
  }
}
