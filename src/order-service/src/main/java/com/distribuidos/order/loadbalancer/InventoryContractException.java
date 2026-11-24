package com.distribuidos.order.loadbalancer;

/**
 * Inventario rechazó la petición de {@code order-service} o respondió algo inválido ({@code 502}).
 * Es un error de integración, no del cliente.
 */
public class InventoryContractException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /**
   * Crea la excepción.
   *
   * @param message detalle.
   * @param cause causa, o {@code null}.
   */
  public InventoryContractException(String message, Throwable cause) {
    super(message, cause);
  }
}
