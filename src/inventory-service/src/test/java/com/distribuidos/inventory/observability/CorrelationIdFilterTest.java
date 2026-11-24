package com.distribuidos.inventory.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** Unit tests for the correlation/instance filter of {@code inventory-service}. */
class CorrelationIdFilterTest {

  private final CorrelationIdFilter filter =
      new CorrelationIdFilter(new InstanceIdentity("inventory-service-1:8082"));

  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  @Test
  void putsIncomingCorrelationIdAndInstanceInMdcDuringRequestAndClearsThemAfter() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/inventory/PROD-001");
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
        .containsEntry(MdcKeys.INSTANCE_ID, "inventory-service-1:8082");
    assertThat(response.getHeader(MdcKeys.CORRELATION_ID_HEADER)).isEqualTo("trace-123");
    assertThat(MDC.get(MdcKeys.CORRELATION_ID)).isNull();
    assertThat(MDC.get(MdcKeys.INSTANCE_ID)).isNull();
  }

  @Test
  void generatesCorrelationIdWhenMissing() throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(
        new MockHttpServletRequest("GET", "/api/inventory/PROD-001"), response, (req, res) -> {});

    assertThat(response.getHeader(MdcKeys.CORRELATION_ID_HEADER)).isNotBlank();
  }

  @Test
  void setsInstanceHeaderOnEveryResponse() throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(
        new MockHttpServletRequest("GET", "/api/inventory/PROD-001"), response, (req, res) -> {});

    assertThat(response.getHeader(MdcKeys.INSTANCE_ID_HEADER))
        .isEqualTo("inventory-service-1:8082");
  }

  @Test
  void clearsMdcWhenTheChainFails() {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/inventory/PROD-001");
    MockHttpServletResponse response = new MockHttpServletResponse();

    assertThatThrownBy(
            () ->
                filter.doFilter(
                    request,
                    response,
                    (req, res) -> {
                      throw new IllegalStateException("boom");
                    }))
        .isInstanceOf(IllegalStateException.class);

    assertThat(MDC.get(MdcKeys.CORRELATION_ID)).isNull();
    assertThat(MDC.get(MdcKeys.INSTANCE_ID)).isNull();
  }
}
