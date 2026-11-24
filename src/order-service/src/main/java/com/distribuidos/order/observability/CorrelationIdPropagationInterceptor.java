package com.distribuidos.order.observability;

import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/**
 * Propaga el {@code X-Correlation-Id} de la petición en curso a las llamadas salientes, para poder
 * seguir una operación de negocio de extremo a extremo (Gateway → order → inventory).
 */
public class CorrelationIdPropagationInterceptor implements ClientHttpRequestInterceptor {

  @Override
  public ClientHttpResponse intercept(
      HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
    String correlationId = MDC.get(MdcKeys.CORRELATION_ID);
    if (correlationId != null && !request.getHeaders().containsKey(MdcKeys.CORRELATION_ID_HEADER)) {
      request.getHeaders().set(MdcKeys.CORRELATION_ID_HEADER, correlationId);
    }
    return execution.execute(request, body);
  }
}
