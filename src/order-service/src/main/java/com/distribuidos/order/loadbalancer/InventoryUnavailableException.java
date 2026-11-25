package com.distribuidos.order.loadbalancer;

/**
 * Inventario no respondió a tiempo, no estaba disponible o falló ({@code 503}).
 *
 * <p>{@link #reservationMayExist()} indica si la petición pudo llegar a inventario (timeout de
 * lectura, 5xx): en ese caso la reserva pudo hacerse y hay que compensar. Si la conexión ni se
 * estableció, no hay nada que liberar.
 */
public class InventoryUnavailableException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final boolean reservationMayExist;

  /**
   * Crea la excepción.
   *
   * @param message detalle.
   * @param reservationMayExist si la petición pudo llegar a inventario.
   * @param cause causa.
   */
  public InventoryUnavailableException(
      String message, boolean reservationMayExist, Throwable cause) {
    super(message, cause);
    this.reservationMayExist = reservationMayExist;
  }

  /**
   * Si la reserva pudo llegar a aplicarse en inventario.
   *
   * @return {@code true} si hay que compensar.
   */
  public boolean reservationMayExist() {
    return reservationMayExist;
  }
}
