package com.distribuidos.order.datasource;

import com.distribuidos.order.datasource.health.ReplicaHealthRegistry;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Round-Robin entre las réplicas que están en rotación según {@link ReplicaHealthRegistry}.
 *
 * <p>El estado lo mantiene el registro: el sondeo periódico decide la reincorporación y {@link
 * #markDown} retira una réplica en cuanto una petición real falla contra ella.
 */
public class HealthAwareReplicaSelector implements ReplicaSelector {

  private static final String REQUEST_FAILURE = "request-connection-failure";

  private final ReplicaHealthRegistry registry;
  private final AtomicInteger counter = new AtomicInteger();

  /**
   * Crea el selector.
   *
   * @param registry estado de las réplicas.
   */
  public HealthAwareReplicaSelector(ReplicaHealthRegistry registry) {
    this.registry = registry;
  }

  @Override
  public Optional<DbNode> next() {
    List<DbNode> available = registry.replicas().stream().filter(registry::isInRotation).toList();
    if (available.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(available.get(Math.floorMod(counter.getAndIncrement(), available.size())));
  }

  @Override
  public void markDown(DbNode replica) {
    registry.markDown(replica, REQUEST_FAILURE);
  }

  @Override
  public int replicaCount() {
    return registry.replicas().size();
  }
}
