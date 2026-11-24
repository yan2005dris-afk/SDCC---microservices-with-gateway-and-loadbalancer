package com.distribuidos.order.datasource;

/** Ningún nodo de base de datos pudo atender la lectura (réplicas y, si aplica, primario). */
public class ReadNodesUnavailableException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /**
   * Crea la excepción conservando el último fallo de conexión observado.
   *
   * @param cause último fallo de conexión, puede ser {@code null}.
   */
  public ReadNodesUnavailableException(Throwable cause) {
    super("Ningún nodo de base de datos disponible para la lectura", cause);
  }
}
