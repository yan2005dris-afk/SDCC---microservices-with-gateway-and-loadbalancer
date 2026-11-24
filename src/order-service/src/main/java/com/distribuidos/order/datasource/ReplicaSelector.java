package com.distribuidos.order.datasource;

import java.util.Optional;

/**
 * Selección de la réplica que atiende una lectura ordinaria.
 *
 * <p>Es una responsabilidad distinta del balanceo HTTP entre instancias (Spring Cloud
 * LoadBalancer): aquí se elige el nodo PostgreSQL de lectura, no la instancia del microservicio.
 */
public interface ReplicaSelector {

  /**
   * Devuelve la siguiente réplica sana según la política de reparto.
   *
   * @return réplica elegida, o vacío si no queda ninguna sana.
   */
  Optional<DbNode> next();

  /**
   * Retira una réplica del reparto tras un fallo de conexión.
   *
   * @param replica réplica que falló.
   */
  void markDown(DbNode replica);

  /**
   * Número de réplicas configuradas, sanas o no. Acota los intentos de failover.
   *
   * @return número de réplicas.
   */
  int replicaCount();
}
