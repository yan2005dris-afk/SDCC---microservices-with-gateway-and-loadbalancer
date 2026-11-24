package com.distribuidos.order.datasource;

/** Política de consistencia de una lectura. */
public enum ReadPolicy {
  /** Lectura ordinaria: se reparte entre las réplicas sanas, con failover entre ellas. */
  ORDINARY,
  /** Lectura crítica (read-your-writes, P07): siempre se atiende desde el primario. */
  CRITICAL
}
