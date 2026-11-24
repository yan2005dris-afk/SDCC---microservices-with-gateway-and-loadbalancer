package com.distribuidos.order.loadbalancer.dto;

import java.math.BigDecimal;

/**
 * Respuesta de {@code POST /api/inventory/reserve} (200 y 400 por stock insuficiente).
 *
 * @param productCode producto.
 * @param available si la reserva se aplicó.
 * @param remainingStock stock tras la reserva, o disponible si no alcanzó.
 * @param unitPrice precio unitario según inventario.
 * @param productName nombre según inventario.
 * @param message mensaje de inventario.
 */
public record InventoryReserveResponse(
    String productCode,
    Boolean available,
    Integer remainingStock,
    BigDecimal unitPrice,
    String productName,
    String message) {

  /**
   * Indica si la reserva se aplicó.
   *
   * @return {@code true} solo si {@code available} es {@code true}.
   */
  public boolean isAvailable() {
    return Boolean.TRUE.equals(available);
  }
}
