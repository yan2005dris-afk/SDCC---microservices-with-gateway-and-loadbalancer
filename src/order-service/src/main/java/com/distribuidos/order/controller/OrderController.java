package com.distribuidos.order.controller;

import com.distribuidos.order.datasource.DataSourceType;
import com.distribuidos.order.datasource.ReplicaLoadBalancer;
import com.distribuidos.order.datasource.RoutingContextHolder;
import com.distribuidos.order.model.Order;
import com.distribuidos.order.model.OrderItem;
import com.distribuidos.order.repository.OrderRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

  private static final Logger log = LoggerFactory.getLogger(OrderController.class);

  private final OrderRepository orderRepository;
  private final ReplicaLoadBalancer replicaLoadBalancer;
  private final RestClient restClient;
  private final String instanceId;

  public OrderController(
      OrderRepository orderRepository,
      ReplicaLoadBalancer replicaLoadBalancer,
      RestClient.Builder restClientBuilder,
      @Value("${INSTANCE_ID:${spring.application.name}:8081}") String instanceId) {
    this.orderRepository = orderRepository;
    this.replicaLoadBalancer = replicaLoadBalancer;
    this.instanceId = instanceId;
    // Invocación a inventory-service mediante nombre lógico en Eureka (P04)
    this.restClient = restClientBuilder.baseUrl("http://inventory-service").build();
  }

  // P02, P05, P06, P08: Lecturas ordinarias balanceadas entre Réplica 1 y Réplica 2
  @GetMapping
  @Transactional(readOnly = true)
  public ResponseEntity<List<Order>> listOrders() {
    DataSourceType selectedReplica = replicaLoadBalancer.getNextReplica();
    RoutingContextHolder.set(selectedReplica);

    try {
      List<Order> orders = orderRepository.findAll();
      return ResponseEntity.ok()
          .header("X-Instance-Id", instanceId)
          .header("X-Database-Node", selectedReplica.name())
          .body(orders);
    } catch (Exception e) {
      log.error("Fallo al leer de {}", selectedReplica, e);
      replicaLoadBalancer.markReplicaDegraded(selectedReplica);

      // Reintento inmediato en la réplica superviviente o Primary (P06)
      DataSourceType fallback = replicaLoadBalancer.getNextReplica();
      RoutingContextHolder.set(fallback);
      List<Order> orders = orderRepository.findAll();
      return ResponseEntity.ok()
          .header("X-Instance-Id", instanceId)
          .header("X-Database-Node", fallback.name())
          .body(orders);
    } finally {
      RoutingContextHolder.clear();
    }
  }

  // P07: Consistencia de Lectura Crítica (Read-Your-Writes)
  @GetMapping("/{id}")
  @Transactional(readOnly = true)
  public ResponseEntity<?> getOrderById(
      @PathVariable Long id,
      @RequestHeader(value = "X-Consistency", defaultValue = "standard") String consistency) {

    DataSourceType target =
        "critical".equalsIgnoreCase(consistency)
            ? DataSourceType.PRIMARY
            : replicaLoadBalancer.getNextReplica();

    RoutingContextHolder.set(target);

    try {
      return orderRepository
          .findById(id)
          .map(
              order ->
                  ResponseEntity.ok()
                      .header("X-Instance-Id", instanceId)
                      .header("X-Database-Node", target.name())
                      .body(order))
          .orElse(ResponseEntity.notFound().build());
    } finally {
      RoutingContextHolder.clear();
    }
  }

  // P04 & P05: Escritura en PRIMARY previa verificación de stock vía Eureka
  @PostMapping
  @Transactional
  public ResponseEntity<?> createOrder(@RequestBody Map<String, Object> request) {
    // Enrutamiento forzoso a PRIMARY para escrituras
    RoutingContextHolder.set(DataSourceType.PRIMARY);

    try {
      String customerName = (String) request.get("customerName");
      String customerEmail = (String) request.get("customerEmail");
      String productCode = (String) request.get("productCode");
      Integer quantity = (Integer) request.get("quantity");
      Number unitPriceNum = (Number) request.get("unitPrice");

      if (productCode == null || quantity == null || unitPriceNum == null) {
        return ResponseEntity.badRequest()
            .body(Map.of("error", "Datos incompletos para crear orden"));
      }

      BigDecimal unitPrice = BigDecimal.valueOf(unitPriceNum.doubleValue());

      // 1. Comunicación RPC dinámica con inventory-service (P04)
      try {
        var reserveResponse =
            restClient
                .post()
                .uri("/api/inventory/reserve")
                .body(Map.of("productCode", productCode, "quantity", quantity))
                .retrieve()
                .body(Map.class);

        if (reserveResponse == null || Boolean.FALSE.equals(reserveResponse.get("available"))) {
          return ResponseEntity.badRequest().body(Map.of("error", "Stock insuficiente"));
        }
      } catch (Exception ex) {
        log.error("Fallo comunicación inter-servicio con inventory-service", ex);
        return ResponseEntity.badRequest().body(Map.of("error", "Error al verificar inventario"));
      }

      // 2. Persistir pedido en PostgreSQL Primary (P05)
      Order order = new Order();
      order.setOrderNumber("ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
      order.setCustomerName(customerName != null ? customerName : "Cliente General");
      order.setCustomerEmail(customerEmail != null ? customerEmail : "cliente@distribuidos.com");
      order.setStatus("CONFIRMED");

      BigDecimal subtotal = unitPrice.multiply(BigDecimal.valueOf(quantity));
      order.setTotalAmount(subtotal);

      OrderItem item = new OrderItem();
      item.setOrder(order);
      item.setProductCode(productCode);
      item.setProductName("Producto " + productCode);
      item.setQuantity(quantity);
      item.setUnitPrice(unitPrice);
      item.setSubtotal(subtotal);

      order.getItems().add(item);

      Order savedOrder = orderRepository.save(order);

      return ResponseEntity.status(201)
          .header("X-Instance-Id", instanceId)
          .header("X-Database-Node", DataSourceType.PRIMARY.name())
          .body(savedOrder);
    } finally {
      RoutingContextHolder.clear();
    }
  }
}
