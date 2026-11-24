package com.distribuidos.inventory.exception;

/** Raised when no reservation movement exists for an order number. */
public class ReservationNotFoundException extends RuntimeException {

  public ReservationNotFoundException(String orderNumber) {
    super("Reserva no encontrada: " + orderNumber);
  }
}
