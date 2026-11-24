package com.distribuidos.order.datasource.health;

import com.distribuidos.order.datasource.DbNode;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

/** Vista de solo lectura del estado de las réplicas (cabeceras HTTP y actuator). */
public interface ReplicaStatusView {

  /**
   * Retraso de replicación del último sondeo.
   *
   * @param replica réplica.
   * @return retraso en ms, o vacío si no se ha medido.
   */
  OptionalLong lagMs(DbNode replica);

  /**
   * Estado de todas las réplicas, en orden de reparto.
   *
   * @return instantánea por réplica.
   */
  List<Snapshot> snapshot();

  /**
   * Estado de una réplica.
   *
   * @param node réplica.
   * @param lastStatus último sondeo, o vacío si aún no se ha sondeado.
   * @param inRotation si recibe lecturas.
   */
  record Snapshot(DbNode node, Optional<ReplicaStatus> lastStatus, boolean inRotation) {}
}
