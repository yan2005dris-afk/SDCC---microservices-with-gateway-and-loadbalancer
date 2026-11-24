package com.distribuidos.inventory.observability;

/** Claves del contexto de log (MDC) y cabeceras de correlación de {@code inventory-service}. */
public final class MdcKeys {

  /** Cabecera HTTP con el identificador de correlación generado por el Gateway. */
  public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

  /** Cabecera HTTP con la instancia que atendió la petición. */
  public static final String INSTANCE_ID_HEADER = "X-Instance-Id";

  /** Identificador de correlación de la petición en curso. */
  public static final String CORRELATION_ID = "correlationId";

  /** Instancia del microservicio que atiende la petición. */
  public static final String INSTANCE_ID = "instanceId";

  private MdcKeys() {}
}
