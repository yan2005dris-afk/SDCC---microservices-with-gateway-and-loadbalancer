package com.distribuidos.order.service;

/** El pedido no se pudo registrar en el primario tras reservar el stock ({@code 503}). */
public class OrderPersistenceException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /**
   * Crea la excepción.
   *
   * @param orderNumber pedido.
   * @param cause causa.
   */
  public OrderPersistenceException(String orderNumber, Throwable cause) {
    super("No se pudo registrar el pedido " + orderNumber, cause);
  }
}
