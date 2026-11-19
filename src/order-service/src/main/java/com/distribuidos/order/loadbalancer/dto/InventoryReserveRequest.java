package com.distribuidos.order.loadbalancer.dto;

/**
 * Solicitud tipada para reservar stock en {@code inventory-service}.
 *
 * @param productCode código único del producto solicitado.
 * @param quantity cantidad positiva a descontar del inventario disponible.
 */
public record InventoryReserveRequest(String productCode, int quantity) {

  /** Constructor compacto que valida invariantes del contrato RPC. */
  public InventoryReserveRequest {
    if (productCode == null || productCode.isBlank()) {
      throw new IllegalArgumentException("El código de producto es obligatorio");
    }
    if (quantity <= 0) {
      throw new IllegalArgumentException("La cantidad a reservar debe ser mayor a cero");
    }
  }
}
