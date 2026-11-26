package com.distribuidos.order.service;

import com.distribuidos.order.dto.OrderResponse;

/**
 * Resultado de crear un pedido.
 *
 * @param order pedido.
 * @param replayed {@code true} si ya existía por la misma {@code Idempotency-Key} ({@code 200} en
 *     lugar de {@code 201}).
 */
public record OrderCreation(OrderResponse order, boolean replayed) {

  /**
   * Pedido nuevo.
   *
   * @param order pedido.
   * @return resultado.
   */
  public static OrderCreation created(OrderResponse order) {
    return new OrderCreation(order, false);
  }

  /**
   * Pedido existente devuelto por un reintento.
   *
   * @param order pedido.
   * @return resultado.
   */
  public static OrderCreation replayed(OrderResponse order) {
    return new OrderCreation(order, true);
  }
}
