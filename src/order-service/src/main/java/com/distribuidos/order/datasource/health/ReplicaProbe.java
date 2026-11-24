package com.distribuidos.order.datasource.health;

import com.distribuidos.order.datasource.DbNode;

/** Comprueba si una réplica está disponible y apta para lecturas. */
public interface ReplicaProbe {

  /**
   * Sondea una réplica. No lanza excepciones: un fallo se informa como {@link ReplicaState#DOWN}.
   *
   * @param replica réplica a sondear.
   * @return estado observado.
   */
  ReplicaStatus probe(DbNode replica);
}
