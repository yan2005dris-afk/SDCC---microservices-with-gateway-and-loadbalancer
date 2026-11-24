package com.distribuidos.order.datasource;

import java.util.Objects;

/**
 * Nodo de base de datos al que se enruta una operación: el primario o una réplica declarada en
 * {@code sdcc.datasource.replicas}.
 *
 * @param name nombre del nodo; se publica en logs y en la cabecera {@code X-Database-Node}.
 */
public record DbNode(String name) {

  /** Nodo primario: escrituras, lecturas críticas y último recurso de lectura. */
  public static final DbNode PRIMARY = new DbNode("PRIMARY");

  /**
   * Valida el nombre.
   *
   * @param name nombre del nodo.
   */
  public DbNode {
    Objects.requireNonNull(name, "name");
    if (name.isBlank()) {
      throw new IllegalArgumentException("El nombre del nodo no puede estar vacío");
    }
  }

  /**
   * Indica si es el primario.
   *
   * @return {@code true} para el primario.
   */
  public boolean isPrimary() {
    return PRIMARY.equals(this);
  }

  @Override
  public String toString() {
    return name;
  }
}
