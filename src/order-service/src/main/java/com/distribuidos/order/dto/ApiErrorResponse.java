package com.distribuidos.order.dto;

/**
 * Respuesta de error HTTP fuertemente tipada para el controlador de pedidos.
 *
 * @param error descripción legible del error ocurrido.
 */
public record ApiErrorResponse(String error) {}
