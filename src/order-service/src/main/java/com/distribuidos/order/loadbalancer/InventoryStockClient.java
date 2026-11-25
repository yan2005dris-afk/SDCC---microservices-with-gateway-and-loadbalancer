package com.distribuidos.order.loadbalancer;

/**
 * Puerto hacia {@code inventory-service}. Las implementaciones traducen los errores de transporte a
 * excepciones de dominio, para que los servicios no dependan de HTTP.
 */
public interface InventoryStockClient {

  /**
   * Reserva stock para un pedido. Idempotente por {@code orderNumber} en inventario (B4).
   *
   * @param productCode producto.
   * @param quantity cantidad.
   * @param orderNumber pedido que reserva.
   * @return precio y nombre del producto según inventario.
   * @throws StockUnavailableException si no hay stock suficiente.
   * @throws ProductNotFoundException si el producto no existe.
   * @throws InventoryUnavailableException si inventario no responde a tiempo o falla.
   * @throws InventoryContractException si inventario rechaza la petición o responde algo inválido.
   */
  ReservedStock reserve(String productCode, int quantity, String orderNumber);

  /**
   * Libera las reservas de un pedido (compensación). Idempotente; si no había reserva no hace nada.
   *
   * @param orderNumber pedido.
   * @throws InventoryUnavailableException si inventario no responde o falla.
   */
  void release(String orderNumber);
}
