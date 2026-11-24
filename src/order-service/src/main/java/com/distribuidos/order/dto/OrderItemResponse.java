package com.distribuidos.order.dto;

import com.distribuidos.order.model.OrderItem;
import java.math.BigDecimal;

/**
 * Línea de pedido expuesta por la API. Mantiene los mismos campos JSON que la entidad.
 *
 * @param id identificador de la línea.
 * @param productCode código del producto.
 * @param productName nombre del producto.
 * @param quantity cantidad.
 * @param unitPrice precio unitario.
 * @param subtotal subtotal de la línea.
 */
public record OrderItemResponse(
    Long id,
    String productCode,
    String productName,
    Integer quantity,
    BigDecimal unitPrice,
    BigDecimal subtotal) {

  /**
   * Convierte la entidad en DTO. Debe llamarse dentro de la transacción.
   *
   * @param item entidad de línea de pedido.
   * @return DTO de la línea.
   */
  public static OrderItemResponse from(OrderItem item) {
    return new OrderItemResponse(
        item.getId(),
        item.getProductCode(),
        item.getProductName(),
        item.getQuantity(),
        item.getUnitPrice(),
        item.getSubtotal());
  }
}
