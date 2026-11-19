package com.distribuidos.order.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.distribuidos.order.datasource.DataSourceType;
import com.distribuidos.order.datasource.ReplicaLoadBalancer;
import com.distribuidos.order.datasource.RoutingContextHolder;
import com.distribuidos.order.dto.CreateOrderRequest;
import com.distribuidos.order.loadbalancer.InventoryStockClient;
import com.distribuidos.order.loadbalancer.dto.InventoryReserveResponse;
import com.distribuidos.order.model.Order;
import com.distribuidos.order.repository.OrderRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** Pruebas unitarias de {@link OrderController} verificando P04, P05, P06 y P07. */
@ExtendWith(MockitoExtension.class)
class OrderControllerTest {

  @Mock private OrderRepository orderRepository;
  @Mock private ReplicaLoadBalancer replicaLoadBalancer;
  @Mock private InventoryStockClient inventoryStockClient;

  private OrderController controller;

  @BeforeEach
  void setUp() {
    this.controller =
        new OrderController(
            orderRepository, replicaLoadBalancer, inventoryStockClient, "order-service-1:8081");
  }

  @AfterEach
  void tearDown() {
    RoutingContextHolder.clear();
  }

  @Test
  void createOrderReturns201AndRoutesToPrimaryWhenStockAvailable() {
    CreateOrderRequest request =
        new CreateOrderRequest(
            "Juan Perez", "juan@test.com", "PROD-001", 2, new BigDecimal("1899.99"));
    when(inventoryStockClient.reserveStock(any()))
        .thenReturn(new InventoryReserveResponse("PROD-001", true, 48, null));
    when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

    ResponseEntity<?> response = controller.createOrder(request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getHeaders().getFirst("X-Instance-Id")).isEqualTo("order-service-1:8081");
    assertThat(response.getHeaders().getFirst("X-Database-Node")).isEqualTo("PRIMARY");
    Order created = (Order) response.getBody();
    assertThat(created).isNotNull();
    assertThat(created.getStatus()).isEqualTo("CONFIRMED");
    assertThat(created.getTotalAmount()).isEqualByComparingTo("3799.98");
  }

  @Test
  void createOrderReturns400WhenStockIsInsufficient() {
    CreateOrderRequest request =
        new CreateOrderRequest("Ana", "ana@test.com", "PROD-001", 999, new BigDecimal("10.00"));
    when(inventoryStockClient.reserveStock(any()))
        .thenReturn(InventoryReserveResponse.unavailable("PROD-001", "Stock insuficiente"));

    ResponseEntity<?> response = controller.createOrder(request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    verify(orderRepository, never()).save(any());
  }

  @Test
  void listOrdersFailsOverToHealthyReplicaWhenFirstReplicaFails() {
    when(replicaLoadBalancer.getNextReplica())
        .thenReturn(DataSourceType.REPLICA_1, DataSourceType.REPLICA_2);
    when(orderRepository.findAll())
        .thenThrow(new RuntimeException("Replica 1 caida"))
        .thenReturn(List.of(new Order()));

    ResponseEntity<List<Order>> response = controller.listOrders();

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getHeaders().getFirst("X-Database-Node")).isEqualTo("REPLICA_2");
    verify(replicaLoadBalancer).markReplicaDegraded(DataSourceType.REPLICA_1);
  }

  @Test
  void getOrderByIdUsesPrimaryWhenConsistencyIsCritical() {
    Order order = new Order();
    order.setOrderNumber("ORD-12345678");
    when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

    ResponseEntity<Order> response = controller.getOrderById(1L, "critical");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getHeaders().getFirst("X-Database-Node")).isEqualTo("PRIMARY");
    verify(replicaLoadBalancer, never()).getNextReplica();
  }
}
