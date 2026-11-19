package com.distribuidos.order.loadbalancer;

import com.distribuidos.order.loadbalancer.dto.InventoryReserveRequest;
import com.distribuidos.order.loadbalancer.dto.InventoryReserveResponse;

/**
 * Contrato de cliente balanceado para consultar y reservar inventario vía descubrimiento lógico.
 */
public interface InventoryStockClient {

  /**
   * Solicita la reserva atómica de stock para un producto en {@code inventory-service}.
   *
   * @param request datos validados del producto y cantidad requerida.
   * @return resultado fuertemente tipado de la verificación y reserva.
   */
  InventoryReserveResponse reserveStock(InventoryReserveRequest request);
}
