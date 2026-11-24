package com.distribuidos.order.datasource.health;

import java.time.Instant;
import java.util.Objects;
import java.util.OptionalLong;

/**
 * Resultado de un sondeo de una réplica.
 *
 * @param state estado observado.
 * @param lagMs retraso de replicación en milisegundos, si se pudo medir.
 * @param reason motivo cuando el estado no es {@code UP} ({@code ok} en caso contrario).
 * @param checkedAt instante del sondeo.
 */
public record ReplicaStatus(
    ReplicaState state, OptionalLong lagMs, String reason, Instant checkedAt) {

  /**
   * Valida los campos.
   *
   * @param state estado.
   * @param lagMs retraso.
   * @param reason motivo.
   * @param checkedAt instante.
   */
  public ReplicaStatus {
    Objects.requireNonNull(state, "state");
    Objects.requireNonNull(lagMs, "lagMs");
    Objects.requireNonNull(reason, "reason");
    Objects.requireNonNull(checkedAt, "checkedAt");
  }

  /**
   * Réplica apta.
   *
   * @param lagMs retraso medido.
   * @return estado {@code UP}.
   */
  public static ReplicaStatus up(long lagMs) {
    return new ReplicaStatus(ReplicaState.UP, OptionalLong.of(lagMs), "ok", Instant.now());
  }

  /**
   * Réplica que responde pero no es apta.
   *
   * @param lagMs retraso, si se pudo medir.
   * @param reason motivo.
   * @return estado {@code LAGGING}.
   */
  public static ReplicaStatus lagging(OptionalLong lagMs, String reason) {
    return new ReplicaStatus(ReplicaState.LAGGING, lagMs, reason, Instant.now());
  }

  /**
   * Réplica inaccesible.
   *
   * @param reason motivo.
   * @return estado {@code DOWN}.
   */
  public static ReplicaStatus down(String reason) {
    return new ReplicaStatus(ReplicaState.DOWN, OptionalLong.empty(), reason, Instant.now());
  }
}
