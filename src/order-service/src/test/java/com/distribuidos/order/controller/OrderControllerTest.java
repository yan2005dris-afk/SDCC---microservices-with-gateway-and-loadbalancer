package com.distribuidos.order.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.distribuidos.order.datasource.DataSourceType;
import com.distribuidos.order.datasource.ReadNodesUnavailableException;
import com.distribuidos.order.datasource.ReadPolicy;
import com.distribuidos.order.datasource.RoutedResult;
import com.distribuidos.order.dto.OrderItemResponse;
import com.distribuidos.order.dto.OrderResponse;
import com.distribuidos.order.observability.InstanceIdentity;
import com.distribuidos.order.service.OrderCommandService;
import com.distribuidos.order.service.OrderQueryService;
import com.distribuidos.order.service.OrderRejectedException;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Capa web de pedidos: cabeceras de trazabilidad, políticas de lectura y errores. */
@WebMvcTest(OrderController.class)
@Import(InstanceIdentity.class)
@TestPropertySource(properties = "INSTANCE_ID=order-service-1:8081")
class OrderControllerTest {

  @Autowired private MockMvc mvc;
  @MockitoBean private OrderQueryService queryService;
  @MockitoBean private OrderCommandService commandService;

  private static OrderResponse order(long id, String customer) {
    return new OrderResponse(
        id,
        "ORD-ABCDEF12",
        customer,
        "cliente@test.com",
        new BigDecimal("10.00"),
        "CONFIRMED",
        OffsetDateTime.parse("2026-11-23T10:00:00Z"),
        List.of(
            new OrderItemResponse(
                1L,
                "PROD-001",
                "Producto PROD-001",
                1,
                new BigDecimal("10.00"),
                new BigDecimal("10.00"))));
  }

  @Test
  void listReturnsOrdersWithRoutingHeadersAndSameJsonShape() throws Exception {
    when(queryService.listOrders())
        .thenReturn(new RoutedResult<>(List.of(order(1L, "Ana")), DataSourceType.REPLICA_2, 1));

    mvc.perform(get("/api/orders"))
        .andExpect(status().isOk())
        .andExpect(header().string("X-Instance-Id", "order-service-1:8081"))
        .andExpect(header().string("X-Database-Node", "REPLICA_2"))
        .andExpect(header().exists("X-Correlation-Id"))
        .andExpect(jsonPath("$[0].id").value(1))
        .andExpect(jsonPath("$[0].orderNumber").value("ORD-ABCDEF12"))
        .andExpect(jsonPath("$[0].customerName").value("Ana"))
        .andExpect(jsonPath("$[0].status").value("CONFIRMED"))
        .andExpect(jsonPath("$[0].items[0].productCode").value("PROD-001"));
  }

  @Test
  void getByIdUsesCriticalPolicyWithConsistencyHeader() throws Exception {
    when(queryService.findOrder(eq(1L), eq(ReadPolicy.CRITICAL)))
        .thenReturn(new RoutedResult<>(Optional.of(order(1L, "Ana")), DataSourceType.PRIMARY, 1));

    mvc.perform(get("/api/orders/1").header("X-Consistency", "critical"))
        .andExpect(status().isOk())
        .andExpect(header().string("X-Database-Node", "PRIMARY"));
    verify(queryService).findOrder(1L, ReadPolicy.CRITICAL);
  }

  @Test
  void getByIdUsesOrdinaryPolicyByDefault() throws Exception {
    when(queryService.findOrder(eq(1L), eq(ReadPolicy.ORDINARY)))
        .thenReturn(new RoutedResult<>(Optional.of(order(1L, "Ana")), DataSourceType.REPLICA_1, 1));

    mvc.perform(get("/api/orders/1"))
        .andExpect(status().isOk())
        .andExpect(header().string("X-Database-Node", "REPLICA_1"));
    verify(queryService).findOrder(1L, ReadPolicy.ORDINARY);
  }

  @Test
  void getByIdReturns404WhenOrderDoesNotExist() throws Exception {
    when(queryService.findOrder(any(), any()))
        .thenReturn(new RoutedResult<>(Optional.empty(), DataSourceType.REPLICA_1, 1));

    mvc.perform(get("/api/orders/999"))
        .andExpect(status().isNotFound())
        .andExpect(header().string("X-Database-Node", "REPLICA_1"));
  }

  @Test
  void createReturns201FromPrimary() throws Exception {
    when(commandService.createOrder(any())).thenReturn(order(5L, "Juan"));

    mvc.perform(
            post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productCode\":\"PROD-001\",\"quantity\":1,\"unitPrice\":10.00}"))
        .andExpect(status().isCreated())
        .andExpect(header().string("X-Database-Node", "PRIMARY"))
        .andExpect(jsonPath("$.id").value(5));
  }

  @Test
  void createReturns400WithExistingErrorFormatWhenRejected() throws Exception {
    when(commandService.createOrder(any()))
        .thenThrow(new OrderRejectedException("Stock insuficiente"));

    mvc.perform(
            post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productCode\":\"PROD-001\",\"quantity\":999,\"unitPrice\":10.00}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("Stock insuficiente"));
  }

  @Test
  void listReturns503WhenNoDatabaseNodeIsAvailable() throws Exception {
    when(queryService.listOrders()).thenThrow(new ReadNodesUnavailableException(null));

    mvc.perform(get("/api/orders"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(header().string("X-Instance-Id", "order-service-1:8081"))
        .andExpect(jsonPath("$.error").exists());
  }

  @Test
  void incomingCorrelationIdIsEchoed() throws Exception {
    when(queryService.listOrders())
        .thenReturn(new RoutedResult<>(List.of(), DataSourceType.REPLICA_1, 1));

    mvc.perform(get("/api/orders").header("X-Correlation-Id", "trace-123"))
        .andExpect(header().string("X-Correlation-Id", "trace-123"));
  }
}
