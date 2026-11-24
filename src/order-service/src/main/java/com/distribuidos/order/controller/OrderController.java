package com.distribuidos.order.controller;

import com.distribuidos.order.datasource.DbNode;
import com.distribuidos.order.datasource.ReadPolicy;
import com.distribuidos.order.datasource.RoutedResult;
import com.distribuidos.order.datasource.health.ReplicaStatusView;
import com.distribuidos.order.dto.CreateOrderRequest;
import com.distribuidos.order.dto.OrderResponse;
import com.distribuidos.order.observability.InstanceIdentity;
import com.distribuidos.order.service.OrderCommandService;
import com.distribuidos.order.service.OrderQueryService;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API REST de pedidos. Solo traduce HTTP: la lógica de negocio está en los servicios y el
 * enrutamiento entre nodos de base de datos en {@code RoutedReadExecutor}.
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

  static final String HEADER_INSTANCE_ID = "X-Instance-Id";
  static final String HEADER_DATABASE_NODE = "X-Database-Node";
  static final String HEADER_CONSISTENCY = "X-Consistency";
  static final String HEADER_REPLICATION_LAG = "X-Replication-Lag-Ms";
  private static final String CRITICAL_CONSISTENCY = "critical";

  private final OrderQueryService queryService;
  private final OrderCommandService commandService;
  private final InstanceIdentity instanceIdentity;
  private final ReplicaStatusView replicaStatus;

  /**
   * Crea el controlador.
   *
   * @param queryService casos de uso de lectura.
   * @param commandService caso de uso de creación.
   * @param instanceIdentity identidad de esta instancia.
   * @param replicaStatus estado de las réplicas (lag del último sondeo).
   */
  public OrderController(
      OrderQueryService queryService,
      OrderCommandService commandService,
      InstanceIdentity instanceIdentity,
      ReplicaStatusView replicaStatus) {
    this.queryService = queryService;
    this.commandService = commandService;
    this.instanceIdentity = instanceIdentity;
    this.replicaStatus = replicaStatus;
  }

  /**
   * Lista los pedidos repartiendo la lectura entre réplicas (P02, P05, P06, P08).
   *
   * @return pedidos con cabeceras de trazabilidad.
   */
  @GetMapping
  public ResponseEntity<List<OrderResponse>> listOrders() {
    RoutedResult<List<OrderResponse>> result = queryService.listOrders();
    return respond(HttpStatus.OK, result.node(), result.value());
  }

  /**
   * Consulta un pedido. Con {@code X-Consistency: critical} se lee del primario (P07).
   *
   * @param id identificador del pedido.
   * @param consistency política solicitada.
   * @return pedido o {@code 404}.
   */
  @GetMapping("/{id}")
  public ResponseEntity<OrderResponse> getOrderById(
      @PathVariable Long id,
      @RequestHeader(value = HEADER_CONSISTENCY, defaultValue = "standard") String consistency) {
    RoutedResult<Optional<OrderResponse>> result =
        queryService.findOrder(id, toReadPolicy(consistency));
    return result
        .value()
        .map(order -> respond(HttpStatus.OK, result.node(), order))
        .orElseGet(() -> respond(HttpStatus.NOT_FOUND, result.node(), null));
  }

  /**
   * Crea un pedido validando stock en {@code inventory-service} (P04) y escribiendo en el primario
   * (P05).
   *
   * @param request datos del pedido.
   * @return pedido creado ({@code 201}).
   */
  @PostMapping
  public ResponseEntity<OrderResponse> createOrder(@RequestBody CreateOrderRequest request) {
    OrderResponse created = commandService.createOrder(request);
    return respond(HttpStatus.CREATED, DbNode.PRIMARY, created);
  }

  private static ReadPolicy toReadPolicy(String consistency) {
    return CRITICAL_CONSISTENCY.equalsIgnoreCase(consistency)
        ? ReadPolicy.CRITICAL
        : ReadPolicy.ORDINARY;
  }

  private <T> ResponseEntity<T> respond(HttpStatus status, DbNode node, T body) {
    ResponseEntity.BodyBuilder response =
        ResponseEntity.status(status)
            .header(HEADER_INSTANCE_ID, instanceIdentity.id())
            .header(HEADER_DATABASE_NODE, node.name());
    if (!node.isPrimary()) {
      // Lag del último sondeo de la réplica que respondió; no se consulta en cada petición.
      replicaStatus
          .lagMs(node)
          .ifPresent(lag -> response.header(HEADER_REPLICATION_LAG, Long.toString(lag)));
    }
    return response.body(body);
  }
}
