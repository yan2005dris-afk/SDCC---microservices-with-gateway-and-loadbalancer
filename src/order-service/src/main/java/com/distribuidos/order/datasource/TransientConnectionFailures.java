package com.distribuidos.order.datasource;

import java.sql.SQLException;
import java.sql.SQLNonTransientConnectionException;
import java.sql.SQLTransientConnectionException;
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
        && sql.getSQLState().startsWith(CONNECTION_EXCEPTION_SQLSTATE_CLASS);
  }
}
