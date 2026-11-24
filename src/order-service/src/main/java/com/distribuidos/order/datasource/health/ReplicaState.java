package com.distribuidos.order.datasource.health;

/** Estado de una réplica según el último sondeo. */
public enum ReplicaState {
  /** En recuperación, replicando ({@code streaming}) y con lag aceptable. */
  UP,
  /** Responde, pero no es apta: no replica o va demasiado retrasada. */
  LAGGING,
  /** Sin conexión o sin respuesta dentro del tiempo de sondeo. */
  DOWN
}
