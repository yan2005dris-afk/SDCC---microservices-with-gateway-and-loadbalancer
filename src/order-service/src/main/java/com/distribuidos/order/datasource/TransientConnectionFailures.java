package com.distribuidos.order.datasource;

import java.sql.SQLException;
import java.sql.SQLNonTransientConnectionException;
import java.sql.SQLTransientConnectionException;
import java.util.Set;
import org.hibernate.exception.JDBCConnectionException;
import org.springframework.transaction.CannotCreateTransactionException;

/**
 * Clasifica los errores que justifican reintentar una lectura en otro nodo.
 *
 * <p>Solo los fallos de <b>conexión</b> se reintentan: el nodo no está disponible, pero la misma
 * consulta puede funcionar en otro. Un error de SQL o de lógica fallaría igual en cualquier nodo y
 * se propaga sin reintentar. La detección recorre la cadena de causas porque JPA, Hibernate, Hikari
 * y Spring envuelven el error original de distintas formas.
 */
public final class TransientConnectionFailures {

  /** Clase SQLState estándar (SQL:2011) para "connection exception". */
  private static final String CONNECTION_EXCEPTION_SQLSTATE_CLASS = "08";

  /**
   * SQLState de PostgreSQL cuando el servidor se apaga o cae con la conexión abierta (clase 57,
   * "operator intervention"): {@code 57P01} admin_shutdown, {@code 57P02} crash_shutdown
   * ("unexpected postmaster exit") y {@code 57P03} cannot_connect_now (arrancando o apagándose). No
   * son de la clase {@code 08}, pero el nodo no está disponible; Hikari también los trata como
   * conexión perdida.
   */
  private static final Set<String> SERVER_SHUTDOWN_SQLSTATES = Set.of("57P01", "57P02", "57P03");

  private static final int MAX_CAUSE_DEPTH = 16;

  private TransientConnectionFailures() {}

  /**
   * Indica si el error, o alguna de sus causas, es un fallo de conexión con el nodo.
   *
   * @param error error producido al ejecutar la lectura.
   * @return {@code true} si se puede reintentar en otro nodo.
   */
  public static boolean isTransient(Throwable error) {
    Throwable current = error;
    for (int depth = 0; current != null && depth < MAX_CAUSE_DEPTH; depth++) {
      if (isConnectionFailure(current)) {
        return true;
      }
      Throwable cause = current.getCause();
      current = (cause == current) ? null : cause;
    }
    return false;
  }

  private static boolean isConnectionFailure(Throwable error) {
    if (error instanceof SQLTransientConnectionException
        || error instanceof SQLNonTransientConnectionException
        || error instanceof JDBCConnectionException
        || error instanceof CannotCreateTransactionException) {
      return true;
    }
    return error instanceof SQLException sql
        && sql.getSQLState() != null
        && (sql.getSQLState().startsWith(CONNECTION_EXCEPTION_SQLSTATE_CLASS)
            || SERVER_SHUTDOWN_SQLSTATES.contains(sql.getSQLState()));
  }
}
