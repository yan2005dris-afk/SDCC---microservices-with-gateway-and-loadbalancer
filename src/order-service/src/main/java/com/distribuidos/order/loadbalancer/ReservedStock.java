package com.distribuidos.order.loadbalancer;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Reserva confirmada por inventario. El precio y el nombre del pedido salen de aquí, no del cliente
 * (ORD-06).
 *
 * @param productCode producto reservado.
 * @param productName nombre según inventario.
 * @param unitPrice precio unitario según inventario.
 */
public record ReservedStock(String productCode, String productName, BigDecimal unitPrice) {

  /**
   * Valida los campos.
   *
   * @param productCode producto.
   * @param productName nombre.
   * @param unitPrice precio.
   */
  public ReservedStock {
    Objects.requireNonNull(productCode, "productCode");
    Objects.requireNonNull(productName, "productName");
    Objects.requireNonNull(unitPrice, "unitPrice");
  }
}
