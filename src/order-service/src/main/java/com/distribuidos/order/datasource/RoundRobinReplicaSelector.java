package com.distribuidos.order.datasource;

import java.sql.Connection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Reparto Round-Robin entre las réplicas sanas, con reincorporación periódica de las degradadas.
 *
 * <p>Una réplica sale del reparto cuando una lectura falla por conexión ({@link #markDown}) y
 * vuelve cuando el chequeo periódico consigue conectarse (P08). El chequeo de aptitud basado en el
 * estado de replicación y el lag se incorpora en B3.
 */
public class RoundRobinReplicaSelector implements ReplicaSelector {

  private static final Logger log = LoggerFactory.getLogger(RoundRobinReplicaSelector.class);
  private static final int VALIDATION_TIMEOUT_SECONDS = 2;

  private final Map<DataSourceType, DataSource> replicas;
  private final Map<DataSourceType, AtomicBoolean> healthy;
  private final AtomicInteger counter = new AtomicInteger();

  /**
   * Crea el selector con las réplicas en el orden de reparto.
   *
   * @param replicas réplicas de lectura indexadas por su nodo, en orden de reparto.
   */
  public RoundRobinReplicaSelector(Map<DataSourceType, DataSource> replicas) {
    if (replicas.isEmpty()) {
      throw new IllegalArgumentException("Se requiere al menos una réplica de lectura");
    }
    this.replicas = Collections.unmodifiableMap(new LinkedHashMap<>(replicas));
    Map<DataSourceType, AtomicBoolean> state = new LinkedHashMap<>();
    replicas.keySet().forEach(node -> state.put(node, new AtomicBoolean(true)));
    this.healthy = Collections.unmodifiableMap(state);
  }

  @Override
  public Optional<DataSourceType> next() {
    List<DataSourceType> available =
        healthy.entrySet().stream().filter(e -> e.getValue().get()).map(Map.Entry::getKey).toList();
    if (available.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(available.get(Math.floorMod(counter.getAndIncrement(), available.size())));
  }

  @Override
  public void markDown(DataSourceType replica) {
    AtomicBoolean flag = healthy.get(replica);
    if (flag != null && flag.compareAndSet(true, false)) {
      log.warn("replica.state name={} from=UP to=DOWN reason=connection-failure", replica);
    }
  }

  @Override
  public int replicaCount() {
    return replicas.size();
  }

  /**
   * Indica si una réplica participa actualmente en el reparto.
   *
   * @param replica réplica consultada.
   * @return {@code true} si está sana.
   */
  public boolean isHealthy(DataSourceType replica) {
    AtomicBoolean flag = healthy.get(replica);
    return flag != null && flag.get();
  }

  /** Reincorpora las réplicas degradadas que vuelven a aceptar conexiones (P08). */
  @Scheduled(fixedDelay = 5000)
  public void recoverDegradedReplicas() {
    replicas.forEach(
        (node, dataSource) -> {
          AtomicBoolean flag = healthy.get(node);
          if (!flag.get() && canConnect(dataSource) && flag.compareAndSet(false, true)) {
            log.info("replica.state name={} from=DOWN to=UP reason=connection-restored", node);
          }
        });
  }

  private static boolean canConnect(DataSource dataSource) {
    try (Connection connection = dataSource.getConnection()) {
      return connection.isValid(VALIDATION_TIMEOUT_SECONDS);
    } catch (Exception ex) {
      return false;
    }
  }
}
