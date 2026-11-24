package com.distribuidos.order.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {

  private final CorrelationIdFilter filter =
      new CorrelationIdFilter(new InstanceIdentity("order-service-1:8081"));

  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  @Test
  void putsIncomingCorrelationIdInMdcDuringRequestAndClearsItAfter() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders");
    request.addHeader(MdcKeys.CORRELATION_ID_HEADER, "trace-123");
    MockHttpServletResponse response = new MockHttpServletResponse();
    Map<String, String> seen = new HashMap<>();

    filter.doFilter(
        request,
        response,
        (req, res) -> {
          seen.put(MdcKeys.CORRELATION_ID, MDC.get(MdcKeys.CORRELATION_ID));
          seen.put(MdcKeys.INSTANCE_ID, MDC.get(MdcKeys.INSTANCE_ID));
        });

    assertThat(seen)
        .containsEntry(MdcKeys.CORRELATION_ID, "trace-123")
        .containsEntry(MdcKeys.INSTANCE_ID, "order-service-1:8081");
    assertThat(response.getHeader(MdcKeys.CORRELATION_ID_HEADER)).isEqualTo("trace-123");
    assertThat(MDC.get(MdcKeys.CORRELATION_ID)).isNull();
    assertThat(MDC.get(MdcKeys.INSTANCE_ID)).isNull();
  }

  @Test
  void generatesCorrelationIdWhenMissing() throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(new MockHttpServletRequest("GET", "/api/orders"), response, (req, res) -> {});

    assertThat(response.getHeader(MdcKeys.CORRELATION_ID_HEADER)).isNotBlank();
  }

  @Test
  void propagationInterceptorCopiesCorrelationIdToOutgoingRequest() throws Exception {
    MDC.put(MdcKeys.CORRELATION_ID, "trace-456");
    MockClientHttpRequest outgoing =
        new MockClientHttpRequest(HttpMethod.POST, URI.create("http://inventory-service/x"));
    ClientHttpRequestExecution execution = mock(ClientHttpRequestExecution.class);
    when(execution.execute(any(), any())).thenReturn(mock(ClientHttpResponse.class));

    new CorrelationIdPropagationInterceptor().intercept(outgoing, new byte[0], execution);

    HttpHeaders headers = outgoing.getHeaders();
    assertThat(headers.getFirst(MdcKeys.CORRELATION_ID_HEADER)).isEqualTo("trace-456");
  }
}
