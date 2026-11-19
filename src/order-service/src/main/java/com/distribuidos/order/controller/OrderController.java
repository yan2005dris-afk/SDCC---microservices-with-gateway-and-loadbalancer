package com.distribuidos.order.controller;

import com.distribuidos.order.datasource.DataSourceType;
import com.distribuidos.order.datasource.ReplicaLoadBalancer;
import com.distribuidos.order.datasource.RoutingContextHolder;
import com.distribuidos.order.dto.ApiErrorResponse;
import com.distribuidos.order.dto.CreateOrderRequest;
import com.distribuidos.order.loadbalancer.InventoryStockClient;
import com.distribuidos.order.loadbalancer.dto.InventoryReserveRequest;
import com.distribuidos.order.loadbalancer.dto.InventoryReserveResponse;
import com.distribuidos.order.model.Order;
import com.distribuidos.order.model.OrderItem;
import com.distribuidos.order.repository.OrderRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;

/** Controlador REST para creación y consulta balanceada de pedidos. */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

  private static final Logger log = LoggerFactory.getLogger(OrderController.class);
  private static final String HEADER_INSTANCE_ID = "X-Instance-Id";
  private static final String HEADER_DATABASE_NODE = "X-Database-Node";
  private static final String CRITICAL_CONSISTENCY = "critical";

  private final OrderRepository orderRepository;
  private final ReplicaLoadBalancer replicaLoadBalancer;
  private final InventoryStockClient inventoryStockClient;
  private final String instanceId;

  /**
   * Construye el controlador inyectando persistencia, balanceador de réplicas y cliente RPC.
   *
   * @param orderRepository repositorio JPA de pedidos.
   * @param replicaLoadBalancer balanceador de réplicas PostgreSQL.
   * @param inventoryStockClient cliente balanceado para reserva de inventario.
   * @param instanceId identificador de la instancia actual.
   */
  public OrderController(
      OrderRepository orderRepository,
      ReplicaLoadBalancer replicaLoadBalancer,
      InventoryStockClient inventoryStockClient,
      @Value("${INSTANCE_ID:${spring.application.name}:8081}") String instanceId) {
    this.orderRepository = orderRepository;
    this.replicaLoadBalancer = replicaLoadBalancer;
    this.inventoryStockClient = inventoryStockClient;
    this.instanceId = instanceId;
  }

  /**
   * Lista todos los pedidos distribuyendo lecturas en Round-Robin entre réplicas (P02, P05, P06,
   * P08).
   *
   * @return lista de pedidos con encabezados de trazabilidad.
   */
  @GetMapping
  @Transactional(readOnly = true)
  public ResponseEntity<List<Order>> listOrders() {
    DataSourceType selectedReplica = replicaLoadBalancer.getNextReplica();
    try {
      return buildResponse(HttpStatus.OK, selectedReplica, readOrdersFromNode(selectedReplica));
    } catch (Exception ex) {
      return handleReplicaFailure(selectedReplica, ex);
    }
  }

  /**
   * Consulta un pedido por ID soportando lectura crítica en Primary (P07).
   *
   * @param id identificador del pedido.
   * @param consistency política solicitada mediante {@code X-Consistency}.
   * @return pedido encontrado o {@code 404 Not Found}.
   */
  @GetMapping("/{id}")
  @Transactional(readOnly = true)
  public ResponseEntity<Order> getOrderById(
      @PathVariable Long id,
      @RequestHeader(value = "X-Consistency", defaultValue = "standard") String consistency) {
    DataSourceType target = resolveReadTarget(consistency);
    RoutingContextHolder.set(target);
    try {
      return orderRepository
          .findById(id)
          .map(order -> buildResponse(HttpStatus.OK, target, order))
          .orElseGet(() -> ResponseEntity.notFound().build());
    } finally {
      RoutingContextHolder.clear();
    }
  }

  /**
   * Valida inventario mediante llamada RPC balanceada y persiste el pedido en Primary (P04, P05).
   *
   * @param request datos tipados del pedido a crear.
   * @return pedido confirmado ({@code 201}) o error de validación/stock ({@code 400}).
   */
  @PostMapping
  @Transactional
  public ResponseEntity<?> createOrder(@RequestBody CreateOrderRequest request) {
    if (request == null || !request.hasRequiredFields()) {
      return ResponseEntity.badRequest()
          .body(new ApiErrorResponse("Datos incompletos para crear orden"));
    }
    ResponseEntity<ApiErrorResponse> stockRejection = tryReserveStock(request);
    if (stockRejection != null) {
      return stockRejection;
    }
    return persistConfirmedOrder(request);
  }

  private List<Order> readOrdersFromNode(DataSourceType node) {
    RoutingContextHolder.set(node);
    try {
      return orderRepository.findAll();
    } finally {
      RoutingContextHolder.clear();
    }
  }

  private ResponseEntity<List<Order>> handleReplicaFailure(
      DataSourceType failedReplica, Exception error) {
    log.error("Fallo al leer de {}", failedReplica, error);
    replicaLoadBalancer.markReplicaDegraded(failedReplica);
    DataSourceType fallback = replicaLoadBalancer.getNextReplica();
    return buildResponse(HttpStatus.OK, fallback, readOrdersFromNode(fallback));
  }

  private DataSourceType resolveReadTarget(String consistency) {
    return CRITICAL_CONSISTENCY.equalsIgnoreCase(consistency)
        ? DataSourceType.PRIMARY
        : replicaLoadBalancer.getNextReplica();
  }

  private ResponseEntity<ApiErrorResponse> tryReserveStock(CreateOrderRequest request) {
    try {
      InventoryReserveResponse response =
          inventoryStockClient.reserveStock(
              new InventoryReserveRequest(request.productCode(), request.quantity()));
      if (!response.isAvailable()) {
        return ResponseEntity.badRequest().body(new ApiErrorResponse("Stock insuficiente"));
      }
      return null;
    } catch (RestClientException ex) {
      log.error("Fallo comunicación inter-servicio con inventory-service", ex);
      return ResponseEntity.badRequest()
          .body(new ApiErrorResponse("Error al verificar inventario"));
    }
  }

  private ResponseEntity<Order> persistConfirmedOrder(CreateOrderRequest request) {
    RoutingContextHolder.set(DataSourceType.PRIMARY);
    try {
      Order savedOrder = orderRepository.save(buildOrder(request));
      return buildResponse(HttpStatus.CREATED, DataSourceType.PRIMARY, savedOrder);
    } finally {
      RoutingContextHolder.clear();
    }
  }

  private Order buildOrder(CreateOrderRequest request) {
    BigDecimal subtotal = request.unitPrice().multiply(BigDecimal.valueOf(request.quantity()));
    Order order = new Order();
    order.setOrderNumber("ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
    order.setCustomerName(request.resolvedCustomerName());
    order.setCustomerEmail(request.resolvedCustomerEmail());
    order.setStatus("CONFIRMED");
    order.setTotalAmount(subtotal);
    order.getItems().add(buildOrderItem(order, request, subtotal));
    return order;
  }

  private OrderItem buildOrderItem(Order order, CreateOrderRequest request, BigDecimal subtotal) {
    OrderItem item = new OrderItem();
    item.setOrder(order);
    item.setProductCode(request.productCode());
    item.setProductName("Producto " + request.productCode());
    item.setQuantity(request.quantity());
    item.setUnitPrice(request.unitPrice());
    item.setSubtotal(subtotal);
    return item;
  }

  private <T> ResponseEntity<T> buildResponse(HttpStatus status, DataSourceType dbNode, T body) {
    return ResponseEntity.status(status)
        .header(HEADER_INSTANCE_ID, instanceId)
        .header(HEADER_DATABASE_NODE, dbNode.name())
        .body(body);
  }
}
