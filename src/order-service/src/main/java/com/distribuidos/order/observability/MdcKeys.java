package com.distribuidos.order.observability;

/** Claves del contexto de log (MDC) y cabecera de correlación compartidas por el servicio. */
public final class MdcKeys {

  /** Cabecera HTTP con el identificador de correlación generado por el Gateway. */
  public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

  /** Identificador de correlación de la petición en curso. */
  public static final String CORRELATION_ID = "correlationId";

  /** Instancia del microservicio que atiende la petición. */
  public static final String INSTANCE_ID = "instanceId";

  /** Nodo de base de datos usado por la operación en curso. */
  public static final String DB_NODE = "dbNode";

  private MdcKeys() {}
}
