package com.distribuidos.inventory.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.distribuidos.inventory.exception.ProductNotFoundException;
import com.distribuidos.inventory.exception.StockUnavailableException;
import com.distribuidos.inventory.repository.InventoryProductRepository;
import com.distribuidos.inventory.service.InventoryReservationService;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@WebMvcTest(InventoryController.class)
class InventoryControllerTest {

  private static final String RESERVE_URL = "/api/inventory/reserve";

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
  void validRequestDelegatesToService() throws Exception {
    when(reservationService.reserveStock("PROD-001", 5)).thenReturn(5);

    performReserve(
            "{\"productCode\":\"PROD-001\",\"quantity\":5,\"orderNumber\":\"ORD-20250101-001\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.available").value(true))
        .andExpect(jsonPath("$.remainingStock").value(5));

    verify(reservationService).reserveStock("PROD-001", 5);
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
    when(reservationService.reserveStock("PROD-999", 1))
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
    when(reservationService.reserveStock("PROD-001", 5))
        .thenThrow(new StockUnavailableException("PROD-001", 2));

    performReserve("{\"productCode\":\"PROD-001\",\"quantity\":5}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.productCode").value("PROD-001"))
        .andExpect(jsonPath("$.available").value(false))
        .andExpect(jsonPath("$.remainingStock").value(2))
        .andExpect(jsonPath("$.message").value("Stock insuficiente. Disponible: 2"));
  }

  @Test
  void unexpectedFailureReturns500ProblemDetail() throws Exception {
    when(reservationService.reserveStock("PROD-001", 1))
        .thenThrow(new IllegalStateException("boom"));

    expectProblemDetail(
        performReserve("{\"productCode\":\"PROD-001\",\"quantity\":1}"),
        500,
        "Internal Server Error",
        "Error interno del servidor",
        RESERVE_URL);
  }

  private void expectBadRequestProblem(ResultActions result, String detail) throws Exception {
    expectProblemDetail(result, 400, "Bad Request", detail, RESERVE_URL);
  }

  private void expectProblemDetail(
      ResultActions result, int status, String title, String detail, String instance)
      throws Exception {
    result
        .andExpect(status().is(status))
        .andExpect(jsonPath("$.type").value("about:blank"))
        .andExpect(jsonPath("$.title").value(title))
        .andExpect(jsonPath("$.status").value(status))
        .andExpect(jsonPath("$.detail").value(detail))
        .andExpect(jsonPath("$.instance").value(instance));
  }

  private ResultActions performReserve(String body) throws Exception {
    return mockMvc.perform(post(RESERVE_URL).contentType(MediaType.APPLICATION_JSON).content(body));
  }
}
