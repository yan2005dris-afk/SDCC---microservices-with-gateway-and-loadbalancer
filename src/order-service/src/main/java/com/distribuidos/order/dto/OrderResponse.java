package com.distribuidos.order.dto;

import com.distribuidos.order.model.Order;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Pedido expuesto por la API. Mantiene los mismos campos JSON que se devolvían al serializar la
 * entidad, para no romper a los clientes existentes, y desacopla la API del modelo JPA.
 *
 * @param id identificador del pedido.
 * @param orderNumber número de pedido.
 * @param customerName nombre del cliente.
 * @param customerEmail correo del cliente.
 * @param totalAmount importe total.
 * @param status estado del pedido.
 * @param createdAt fecha de creación.
 * @param items líneas del pedido.
 */
public record OrderResponse(
    Long id,
    String orderNumber,
    String customerName,
    String customerEmail,
    BigDecimal totalAmount,
    String status,
    OffsetDateTime createdAt,
    List<OrderItemResponse> items) {

  /**
   * Convierte la entidad en DTO. Debe llamarse dentro de la transacción, porque recorre las líneas
   * del pedido.
   *
   * @param order entidad de pedido.
   * @return DTO del pedido.
   */
  public static OrderResponse from(Order order) {
    return new OrderResponse(
        order.getId(),
        order.getOrderNumber(),
        order.getCustomerName(),
        order.getCustomerEmail(),
        order.getTotalAmount(),
        order.getStatus(),
        order.getCreatedAt(),
        order.getItems().stream().map(OrderItemResponse::from).toList());
  }
}
