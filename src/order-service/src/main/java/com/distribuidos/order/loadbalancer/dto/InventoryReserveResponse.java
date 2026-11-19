package com.distribuidos.order.loadbalancer.dto;

/**
 * Respuesta tipada de {@code POST /api/inventory/reserve} en {@code inventory-service}.
 *
 * @param productCode código del producto consultado.
 * @param available indicador de disponibilidad suficiente.
 * @param remainingStock stock restante tras la reserva, si aplica.
 * @param error mensaje de error de negocio en caso de rechazo.
 */
public record InventoryReserveResponse(
    String productCode, Boolean available, Integer remainingStock, String error) {

  /**
   * Indica de forma segura si la reserva fue aprobada por el servicio de inventario.
   *
   * @return {@code true} únicamente cuando {@code available} es {@link Boolean#TRUE}.
   */
  public boolean isAvailable() {
    return Boolean.TRUE.equals(available);
  }

  /**
   * Crea una respuesta de rechazo controlado ante indisponibilidad o respuesta inválida.
   *
   * @param productCode código de producto afectado.
   * @param error motivo del rechazo.
   * @return instancia no disponible.
   */
  public static InventoryReserveResponse unavailable(String productCode, String error) {
    return new InventoryReserveResponse(productCode, Boolean.FALSE, 0, error);
  }
}
