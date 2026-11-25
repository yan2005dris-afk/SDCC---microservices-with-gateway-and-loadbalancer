package com.distribuidos.inventory.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.distribuidos.inventory.dto.ReleaseResponse;
import com.distribuidos.inventory.dto.ReservationResponse;
import com.distribuidos.inventory.exception.ProductNotFoundException;
import com.distribuidos.inventory.exception.ReservationNotFoundException;
import com.distribuidos.inventory.exception.StockUnavailableException;
import com.distribuidos.inventory.observability.InstanceIdentity;
import com.distribuidos.inventory.repository.InventoryProductRepository;
import com.distribuidos.inventory.service.InventoryReservationService;
import com.distribuidos.inventory.web.ApiExceptionHandler;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

@WebMvcTest(InventoryController.class)
@Import(InstanceIdentity.class)
class InventoryControllerTest {

  private static final String RESERVE_URL = "/api/inventory/reserve";
  private static final String RELEASE_URL = "/api/inventory/release";

  @Autowired private MockMvc mockMvc;

  @MockitoBean private InventoryProductRepository repository;

  @MockitoBean private InventoryReservationService reservationService;

  @Test
  void rejectsNonNumericQuantityWithProblemDetail() throws Exception {
    expectBadRequestProblem(
        performReserve("{\"productCode\":\"PROD-001\",\"quantity\":\"abc\"}"),
        "Cuerpo de solicitud inválido");
  }

  @Test
  void rejectsFractionalQuantityWithProblemDetail() throws Exception {
    expectBadRequestProblem(
        performReserve("{\"productCode\":\"PROD-001\",\"quantity\":2.0}"),
        "Cuerpo de solicitud inválido");
  }

  @Test
  void rejectsNegativeQuantityWithProblemDetail() throws Exception {
    expectBadRequestProblem(
        performReserve("{\"productCode\":\"PROD-001\",\"quantity\":-1}"), "quantity debe ser > 0");
  }

  @Test
  void rejectsZeroQuantityWithProblemDetail() throws Exception {
    expectBadRequestProblem(
        performReserve("{\"productCode\":\"PROD-001\",\"quantity\":0}"), "quantity debe ser > 0");
  }

  @Test
  void rejectsBlankProductCodeWithProblemDetail() throws Exception {
    expectBadRequestProblem(
        performReserve("{\"productCode\":\"\",\"quantity\":5}"),
        "productCode no puede estar vacío");
  }

  @Test
  void rejectsMissingQuantityWithProblemDetail() throws Exception {
    expectBadRequestProblem(
        performReserve("{\"productCode\":\"PROD-001\"}"), "quantity es requerido");
    verifyNoInteractions(reservationService);
  }

  @Test
  void rejectsExplicitNullQuantityWithProblemDetail() throws Exception {
    expectBadRequestProblem(
        performReserve("{\"productCode\":\"PROD-001\",\"quantity\":null}"),
        "quantity es requerido");
    verifyNoInteractions(reservationService);
  }

  @Test
  void rejectsMissingProductCodeWithProblemDetail() throws Exception {
    expectBadRequestProblem(performReserve("{\"quantity\":5}"), "productCode no puede estar vacío");
    verifyNoInteractions(reservationService);
  }

  @Test
  void requestWithoutOrderNumberDelegatesWithNullOrderNumber() throws Exception {
    when(reservationService.reserveStock("PROD-001", 5, null))
        .thenReturn(
            new ReservationResponse(
                "PROD-001",
                true,
                5,
                new BigDecimal("1899.99"),
                "Laptop Dell XPS 15",
                "Reserva confirmada"));

    performReserve("{\"productCode\":\"PROD-001\",\"quantity\":5}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.productCode").value("PROD-001"));

    verify(reservationService).reserveStock("PROD-001", 5, null);
  }

  @Test
  void validRequestDelegatesToService() throws Exception {
    when(reservationService.reserveStock("PROD-001", 5, "ORD-20250101-001"))
        .thenReturn(
            new ReservationResponse(
                "PROD-001",
                true,
                5,
                new BigDecimal("1899.99"),
                "Laptop Dell XPS 15",
                "Reserva confirmada"));

    performReserve(
            "{\"productCode\":\"PROD-001\",\"quantity\":5,\"orderNumber\":\"ORD-20250101-001\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.productCode").value("PROD-001"))
        .andExpect(jsonPath("$.available").value(true))
        .andExpect(jsonPath("$.remainingStock").value(5))
        .andExpect(jsonPath("$.unitPrice").value(1899.99))
        .andExpect(jsonPath("$.productName").value("Laptop Dell XPS 15"))
        .andExpect(jsonPath("$.message").value("Reserva confirmada"));

    verify(reservationService).reserveStock("PROD-001", 5, "ORD-20250101-001");
  }

  @Test
  void getUnknownProductReturns404ProblemDetail() throws Exception {
    when(repository.findByProductCode("PROD-999")).thenReturn(Optional.empty());

    expectProblemDetail(
        mockMvc.perform(get("/api/inventory/PROD-999")),
        404,
        "Not Found",
        "Producto no encontrado: PROD-999",
        "/api/inventory/PROD-999");
  }

  @Test
  void reserveUnknownProductReturns404ProblemDetail() throws Exception {
    when(reservationService.reserveStock("PROD-999", 1, null))
        .thenThrow(new ProductNotFoundException("PROD-999"));

    expectProblemDetail(
        performReserve("{\"productCode\":\"PROD-999\",\"quantity\":1}"),
        404,
        "Not Found",
        "Producto no encontrado: PROD-999",
        RESERVE_URL);
  }

  @Test
  void reserveInsufficientStockReturnsCustomBadRequest() throws Exception {
    when(reservationService.reserveStock("PROD-001", 5, null))
        .thenThrow(new StockUnavailableException("PROD-001", 2));

    performReserve("{\"productCode\":\"PROD-001\",\"quantity\":5}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.productCode").value("PROD-001"))
        .andExpect(jsonPath("$.available").value(false))
        .andExpect(jsonPath("$.remainingStock").value(2))
        .andExpect(jsonPath("$.message").value("Stock insuficiente. Disponible: 2"));
  }

  @Test
  void releaseReturnsRestoredStockResponse() throws Exception {
    when(reservationService.releaseStock("ORD-001"))
        .thenReturn(
            ReleaseResponse.of(
                "ORD-001", List.of(ReleaseResponse.ReleasedItem.released("PROD-001", 5))));

    performRelease("{\"orderNumber\":\"ORD-001\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.productCode").value("PROD-001"))
        .andExpect(jsonPath("$.quantity").value(5))
        .andExpect(jsonPath("$.message").value("Stock liberado exitosamente"))
        .andExpect(jsonPath("$.orderNumber").value("ORD-001"))
        .andExpect(jsonPath("$.releases[0].productCode").value("PROD-001"));

    verify(reservationService).releaseStock("ORD-001");
  }

  @Test
  void releaseRejectsBlankOrderNumberWithProblemDetail() throws Exception {
    expectProblemDetail(
        performRelease("{\"orderNumber\":\"\"}"),
        400,
        "Bad Request",
        "orderNumber no puede estar vacío",
        RELEASE_URL);
  }

  @Test
  void releaseWithoutReservationReturns404ProblemDetail() throws Exception {
    when(reservationService.releaseStock("ORD-999"))
        .thenThrow(new ReservationNotFoundException("ORD-999"));

    expectProblemDetail(
        performRelease("{\"orderNumber\":\"ORD-999\"}"),
        404,
        "Not Found",
        "Reserva no encontrada: ORD-999",
        RELEASE_URL);
  }

  @Test
  void unexpectedFailureReturns500ProblemDetail() throws Exception {
    when(reservationService.reserveStock("PROD-001", 1, null))
        .thenThrow(new IllegalStateException("boom"));

    expectProblemDetail(
        performReserve("{\"productCode\":\"PROD-001\",\"quantity\":1}"),
        500,
        "Internal Server Error",
        "Error interno del servidor",
        RESERVE_URL);
  }

  @Test
  void putOnReserveReturns405ProblemDetail() throws Exception {
    expectProblemDetail(
        mockMvc.perform(put(RESERVE_URL).contentType(MediaType.APPLICATION_JSON).content("{}")),
        405,
        "Method Not Allowed",
        "Method 'PUT' is not supported.",
        RESERVE_URL);
  }

  @Test
  void unsupportedMediaTypeOnReserveReturns415ProblemDetail() throws Exception {
    expectProblemDetail(
        mockMvc.perform(post(RESERVE_URL).contentType(MediaType.TEXT_PLAIN).content("not-a-json")),
        415,
        "Unsupported Media Type",
        "Content-Type 'text/plain;charset=UTF-8' is not supported.",
        RESERVE_URL);
  }

  @Test
  void unsupportedMediaTypeOnReleaseReturns415ProblemDetail() throws Exception {
    expectProblemDetail(
        mockMvc.perform(post(RELEASE_URL).contentType(MediaType.TEXT_PLAIN).content("not-a-json")),
        415,
        "Unsupported Media Type",
        "Content-Type 'text/plain;charset=UTF-8' is not supported.",
        RELEASE_URL);
  }

  @Test
  void unmappedPathReturns404ProblemDetail() throws Exception {
    expectProblemDetail(
        mockMvc.perform(get("/api/inventory/a/b")), 404, "Not Found", null, "/api/inventory/a/b");
  }

  @Test
  void unexpectedFailureLogsTheException() throws Exception {
    Logger logger = (Logger) LoggerFactory.getLogger(ApiExceptionHandler.class);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    try {
      when(reservationService.reserveStock("PROD-001", 1, null))
          .thenThrow(new IllegalStateException("boom"));

      mockMvc
          .perform(
              post(RESERVE_URL)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"productCode\":\"PROD-001\",\"quantity\":1}"))
          .andExpect(status().isInternalServerError());

      assertThat(appender.list)
          .anySatisfy(
              event -> {
                assertThat(event.getLevel()).isEqualTo(Level.ERROR);
                assertThat(event.getThrowableProxy()).isNotNull();
                assertThat(event.getThrowableProxy().getMessage()).isEqualTo("boom");
                assertThat(event.getFormattedMessage()).contains(RESERVE_URL);
              });
    } finally {
      logger.detachAppender(appender);
      appender.stop();
    }
  }

  @Test
  void everyResponseCarriesInstanceAndCorrelationHeaders() throws Exception {
    when(repository.findByProductCode("PROD-001")).thenReturn(Optional.empty());

    MvcResult result =
        mockMvc
            .perform(get("/api/inventory/PROD-001"))
            .andExpect(status().isNotFound())
            .andReturn();

    assertThat(result.getResponse().getHeader("X-Instance-Id")).startsWith("inventory-service");
    assertThat(result.getResponse().getHeader("X-Correlation-Id")).isNotBlank();
  }

  @Test
  void incomingCorrelationIdIsPropagatedToTheResponse() throws Exception {
    when(repository.findByProductCode("PROD-001")).thenReturn(Optional.empty());

    MvcResult result =
        mockMvc
            .perform(get("/api/inventory/PROD-001").header("X-Correlation-Id", "trace-abc"))
            .andExpect(status().isNotFound())
            .andReturn();

    assertThat(result.getResponse().getHeader("X-Correlation-Id")).isEqualTo("trace-abc");
  }

  @Test
  void unmappedPathAlsoCarriesInstanceAndCorrelationHeaders() throws Exception {
    MvcResult result =
        mockMvc.perform(get("/api/inventory/a/b")).andExpect(status().isNotFound()).andReturn();

    assertThat(result.getResponse().getHeader("X-Instance-Id")).startsWith("inventory-service");
    assertThat(result.getResponse().getHeader("X-Correlation-Id")).isNotBlank();
  }

  private void expectBadRequestProblem(ResultActions result, String detail) throws Exception {
    expectProblemDetail(result, 400, "Bad Request", detail, RESERVE_URL);
  }

  private void expectProblemDetail(
      ResultActions result, int status, String title, String detail, String instance)
      throws Exception {
    result
        .andExpect(status().is(status))
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value("about:blank"))
        .andExpect(jsonPath("$.title").value(title))
        .andExpect(jsonPath("$.status").value(status));

    if (detail != null) {
      result.andExpect(jsonPath("$.detail").value(detail));
    }

    result.andExpect(jsonPath("$.instance").value(instance));
  }

  private ResultActions performReserve(String body) throws Exception {
    return mockMvc.perform(post(RESERVE_URL).contentType(MediaType.APPLICATION_JSON).content(body));
  }

  private ResultActions performRelease(String body) throws Exception {
    return mockMvc.perform(post(RELEASE_URL).contentType(MediaType.APPLICATION_JSON).content(body));
  }
}
