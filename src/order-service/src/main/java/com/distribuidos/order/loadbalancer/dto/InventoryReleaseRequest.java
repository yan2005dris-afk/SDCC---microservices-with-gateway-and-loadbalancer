package com.distribuidos.order.loadbalancer.dto;

/**
 * Solicitud de {@code POST /api/inventory/release}.
 *
 * @param orderNumber pedido cuyas reservas se liberan.
 */
public record InventoryReleaseRequest(String orderNumber) {}
