package com.distribuidos.order.service;

/**
 * El pedido no se puede crear: datos incompletos, stock insuficiente o inventario no verificable.
 * Se responde con {@code 400} y el mismo mensaje que antes del refactor; la diferenciación de
 * códigos HTTP por causa se aborda en B5.
 */
public class OrderRejectedException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /**
   * Crea la excepción con el motivo expuesto al cliente.
   *
   * @param reason motivo del rechazo.
   */
  public OrderRejectedException(String reason) {
    super(reason);
  }

  /**
   * Crea la excepción con el motivo expuesto al cliente y la causa técnica.
   *
   * @param reason motivo del rechazo.
   * @param cause causa técnica.
   */
  public OrderRejectedException(String reason, Throwable cause) {
    super(reason, cause);
  }
}
