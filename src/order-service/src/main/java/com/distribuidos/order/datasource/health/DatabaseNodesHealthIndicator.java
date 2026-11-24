package com.distribuidos.order.datasource.health;

import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Estado de los nodos de base de datos en {@code /actuator/health} (componente {@code
 * databaseNodes}). Reemplaza al indicador {@code db} de Spring Boot.
 *
 * <p>{@code UP} mientras el primario responda, con {@code degraded: true} si alguna réplica está
 * fuera del balanceo; {@code DOWN} solo si cae el primario. Una réplica caída no debe marcar la
 * instancia como no sana: las lecturas siguen en la otra réplica o en el primario. El estado de las
 * réplicas sale del último sondeo, sin consultarlas aquí.
 */
@Component
public class DatabaseNodesHealthIndicator implements HealthIndicator {

  private static final int PRIMARY_VALIDATION_TIMEOUT_SECONDS = 1;

  private final DataSource primary;
  private final ReplicaStatusView replicas;

  /**
   * Crea el indicador.
   *
   * @param primary pool del primario.
   * @param replicas estado de las réplicas.
   */
  public DatabaseNodesHealthIndicator(
      @Qualifier("primaryDataSource") DataSource primary, ReplicaStatusView replicas) {
    this.primary = primary;
    this.replicas = replicas;
  }

  @Override
  public Health health() {
    Map<String, Object> replicaDetails = new LinkedHashMap<>();
    boolean degraded = false;
    for (ReplicaStatusView.Snapshot snapshot : replicas.snapshot()) {
      replicaDetails.put(snapshot.node().name(), describe(snapshot));
      degraded |= !snapshot.inRotation();
    }
    boolean primaryUp = isPrimaryUp();
    Health.Builder builder = primaryUp ? Health.up() : Health.down();
    return builder
        .withDetail("primary", primaryUp ? "UP" : "DOWN")
        .withDetail("degraded", degraded)
        .withDetail("replicas", replicaDetails)
        .build();
  }

  private boolean isPrimaryUp() {
    try (Connection connection = primary.getConnection()) {
      return connection.isValid(PRIMARY_VALIDATION_TIMEOUT_SECONDS);
    } catch (Exception ex) {
      return false;
    }
  }

  private static Map<String, Object> describe(ReplicaStatusView.Snapshot snapshot) {
    Map<String, Object> detail = new LinkedHashMap<>();
    detail.put("inRotation", snapshot.inRotation());
    snapshot
        .lastStatus()
        .ifPresentOrElse(
            status -> {
              detail.put("state", status.state().name());
              status.lagMs().ifPresent(lag -> detail.put("lagMs", lag));
              detail.put("reason", status.reason());
              detail.put("checkedAt", status.checkedAt().toString());
            },
            () -> detail.put("state", "UNKNOWN"));
    return detail;
  }
}
