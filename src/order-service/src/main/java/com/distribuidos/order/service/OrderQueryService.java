package com.distribuidos.order.service;

import com.distribuidos.order.datasource.ReadPolicy;
import com.distribuidos.order.datasource.RoutedReadExecutor;
import com.distribuidos.order.datasource.RoutedResult;
import com.distribuidos.order.dto.OrderResponse;
import com.distribuidos.order.repository.OrderRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** Casos de uso de lectura de pedidos. El enrutamiento entre nodos lo resuelve el executor. */
@Service
public class OrderQueryService {

  private final OrderRepository orderRepository;
  private final RoutedReadExecutor readExecutor;

  /**
   * Crea el servicio.
   *
   * @param orderRepository repositorio de pedidos.
   * @param readExecutor executor de lecturas enrutadas.
   */
  public OrderQueryService(OrderRepository orderRepository, RoutedReadExecutor readExecutor) {
    this.orderRepository = orderRepository;
    this.readExecutor = readExecutor;
  }

  /**
   * Lista los pedidos desde una réplica (lectura ordinaria, P05/P06).
   *
   * @return pedidos y nodo que respondió.
   */
  public RoutedResult<List<OrderResponse>> listOrders() {
    return readExecutor.execute(
        ReadPolicy.ORDINARY,
        () -> orderRepository.findAll().stream().map(OrderResponse::from).toList());
  }

  /**
   * Busca un pedido por id con la política indicada (P07 si es crítica).
   *
   * @param id identificador del pedido.
   * @param policy política de consistencia.
   * @return pedido, si existe, y nodo que respondió.
   */
  public RoutedResult<Optional<OrderResponse>> findOrder(Long id, ReadPolicy policy) {
    return readExecutor.execute(
        policy, () -> orderRepository.findById(id).map(OrderResponse::from));
  }
}
