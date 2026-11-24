package com.distribuidos.order.datasource.health;

import com.distribuidos.order.datasource.DbNode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Estado de salud de cada réplica y si participa en el balanceo de lecturas.
 *
 * <p>Histéresis asimétrica: una réplica sale del balanceo con un solo resultado que no sea {@code
 * UP} (sondeo o fallo de una petición real) y vuelve tras {@code rejoinThreshold} sondeos {@code
 * UP} consecutivos, para que no entre y salga continuamente. Al arrancar, todas las réplicas están
 * en rotación hasta el primer sondeo.
 *
 * <p>Seguro entre hilos: cada réplica se actualiza de forma atómica con {@link
 * ConcurrentHashMap#compute}; los logs se emiten solo en las transiciones.
 */
public class ReplicaHealthRegistry implements ReplicaStatusView {

  private static final Logger log = LoggerFactory.getLogger(ReplicaHealthRegistry.class);

  private final List<DbNode> replicas;
  private final int rejoinThreshold;
  private final Map<DbNode, Entry> entries = new ConcurrentHashMap<>();

  /**
   * Crea el registro.
   *
   * @param replicas réplicas, en orden de reparto.
   * @param rejoinThreshold sondeos {@code UP} consecutivos para volver al balanceo.
   */
  public ReplicaHealthRegistry(List<DbNode> replicas, int rejoinThreshold) {
    if (replicas.isEmpty() || rejoinThreshold < 1) {
      throw new IllegalArgumentException("Se requiere al menos una réplica y un umbral >= 1");
    }
    this.replicas = List.copyOf(replicas);
    this.rejoinThreshold = rejoinThreshold;
    this.replicas.forEach(node -> entries.put(node, Entry.INITIAL));
  }

  /**
   * Registra el resultado de un sondeo.
   *
   * @param replica réplica sondeada.
   * @param status resultado.
   */
  public void record(DbNode replica, ReplicaStatus status) {
    Entry[] previous = new Entry[1];
    Entry current =
        entries.computeIfPresent(
            replica,
            (node, entry) -> {
              previous[0] = entry;
              return entry.next(status, rejoinThreshold);
            });
    if (current != null) {
      logTransitions(replica, previous[0], current);
    }
  }

  /**
   * Retira de inmediato una réplica tras un fallo de conexión en una petición real.
   *
   * @param replica réplica que falló.
   * @param reason motivo.
   */
  public void markDown(DbNode replica, String reason) {
    record(replica, ReplicaStatus.down(reason));
  }

  /**
   * Indica si la réplica recibe lecturas.
   *
   * @param replica réplica.
   * @return {@code true} si está en rotación.
   */
  public boolean isInRotation(DbNode replica) {
    Entry entry = entries.get(replica);
    return entry != null && entry.inRotation();
  }

  /**
   * Réplicas configuradas, en orden de reparto.
   *
   * @return réplicas.
   */
  public List<DbNode> replicas() {
    return replicas;
  }

  @Override
  public OptionalLong lagMs(DbNode replica) {
    Entry entry = entries.get(replica);
    return entry == null
        ? OptionalLong.empty()
        : entry.status().map(ReplicaStatus::lagMs).orElse(OptionalLong.empty());
  }

  @Override
  public List<Snapshot> snapshot() {
    return replicas.stream().map(node -> entries.get(node).toSnapshot(node)).toList();
  }

  private static void logTransitions(DbNode replica, Entry previous, Entry current) {
    ReplicaStatus status = current.status().orElseThrow();
    Optional<ReplicaState> before = previous.status().map(ReplicaStatus::state);
    if (before.isEmpty() ? status.state() != ReplicaState.UP : before.get() != status.state()) {
      log.warn(
          "replica.state name={} from={} to={} reason={} lagMs={}",
          replica,
          before.map(Enum::name).orElse("UNKNOWN"),
          status.state(),
          status.reason(),
          status.lagMs().isPresent() ? status.lagMs().getAsLong() : "n/a");
    }
    if (previous.inRotation() != current.inRotation()) {
      log.info(
          "replica.rotation name={} action={} consecutiveUp={}",
          replica,
          current.inRotation() ? "JOIN" : "LEAVE",
          current.consecutiveUp());
    }
  }

  /** Estado inmutable de una réplica. */
  private record Entry(Optional<ReplicaStatus> status, boolean inRotation, int consecutiveUp) {

    static final Entry INITIAL = new Entry(Optional.empty(), true, 0);

    Entry next(ReplicaStatus observed, int rejoinThreshold) {
      if (observed.state() != ReplicaState.UP) {
        return new Entry(Optional.of(observed), false, 0);
      }
      int ups = consecutiveUp + 1;
      return new Entry(Optional.of(observed), inRotation || ups >= rejoinThreshold, ups);
    }

    Snapshot toSnapshot(DbNode node) {
      return new Snapshot(node, status, inRotation);
    }
  }
}
