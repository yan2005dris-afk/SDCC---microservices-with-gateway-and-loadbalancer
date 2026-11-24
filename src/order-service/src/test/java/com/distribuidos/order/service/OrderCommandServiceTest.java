package com.distribuidos.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.distribuidos.order.datasource.DbNode;
import com.distribuidos.order.datasource.RoutingContextHolder;
import com.distribuidos.order.dto.CreateOrderRequest;
import com.distribuidos.order.dto.OrderResponse;
import com.distribuidos.order.loadbalancer.InventoryStockClient;
import com.distribuidos.order.loadbalancer.dto.InventoryReserveResponse;
import com.distribuidos.order.model.Order;
import com.distribuidos.order.repository.OrderRepository;
import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.client.ResourceAccessException;

class OrderCommandServiceTest {

  private OrderRepository orderRepository;
  private InventoryStockClient inventoryStockClient;
  private OrderCommandService service;

  @BeforeEach
  void setUp() {
    orderRepository = mock(OrderRepository.class);
    inventoryStockClient = mock(InventoryStockClient.class);
    PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    service = new OrderCommandService(orderRepository, inventoryStockClient, transactionManager);
  }

  private static CreateOrderRequest request(int quantity) {
    return new CreateOrderRequest(
        "Juan Perez", "juan@test.com", "PROD-001", quantity, new BigDecimal("1899.99"));
  }

  @Test
  void createsConfirmedOrderOnPrimaryWhenStockIsAvailable() {
    when(inventoryStockClient.reserveStock(any()))
        .thenReturn(new InventoryReserveResponse("PROD-001", true, 48, null));
    AtomicReference<DbNode> nodeDuringSave = new AtomicReference<>();
    when(orderRepository.save(any(Order.class)))
        .thenAnswer(
            invocation -> {
              nodeDuringSave.set(RoutingContextHolder.get());
              return invocation.getArgument(0);
            });

    OrderResponse created = service.createOrder(request(2));

    assertThat(created.status()).isEqualTo("CONFIRMED");
    assertThat(created.totalAmount()).isEqualByComparingTo("3799.98");
    assertThat(created.orderNumber()).startsWith("ORD-");
    assertThat(created.items()).hasSize(1);
    assertThat(nodeDuringSave.get()).isEqualTo(DbNode.PRIMARY);
    assertThat(RoutingContextHolder.get()).isNull();
  }

  @Test
  void rejectsOrderWhenStockIsInsufficient() {
    when(inventoryStockClient.reserveStock(any()))
        .thenReturn(InventoryReserveResponse.unavailable("PROD-001", "Stock insuficiente"));

    assertThatThrownBy(() -> service.createOrder(request(999)))
        .isInstanceOf(OrderRejectedException.class)
        .hasMessage("Stock insuficiente");
    verify(orderRepository, never()).save(any());
  }

  @Test
  void rejectsOrderWhenInventoryIsUnreachable() {
    when(inventoryStockClient.reserveStock(any()))
        .thenThrow(new ResourceAccessException("connection refused"));

    assertThatThrownBy(() -> service.createOrder(request(1)))
        .isInstanceOf(OrderRejectedException.class)
        .hasMessage("Error al verificar inventario");
    verify(orderRepository, never()).save(any());
  }

  @Test
  void rejectsIncompleteRequestWithoutCallingInventory() {
    CreateOrderRequest incomplete = new CreateOrderRequest(null, null, null, 0, null);

    assertThatThrownBy(() -> service.createOrder(incomplete))
        .isInstanceOf(OrderRejectedException.class)
        .hasMessage("Datos incompletos para crear orden");
    verify(inventoryStockClient, never()).reserveStock(any());
  }
}
