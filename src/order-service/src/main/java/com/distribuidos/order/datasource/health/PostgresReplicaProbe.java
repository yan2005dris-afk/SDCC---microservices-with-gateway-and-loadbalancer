package com.distribuidos.order.datasource.health;

import com.distribuidos.order.datasource.DbNode;
import com.distribuidos.order.datasource.ReplicaDataSources;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.Map;
import java.util.OptionalLong;
import javax.sql.DataSource;

/**
 * Sondeo de aptitud de una réplica PostgreSQL con replicación física por streaming.
 *
 * <p>Usa un pool exclusivo de 1 conexión por réplica, separado del tráfico. Necesita leer {@code
 * pg_stat_wal_receiver}: superusuario o rol {@code pg_monitor} ({@code GRANT pg_monitor TO
 * <usuario>}).
 */
public class PostgresReplicaProbe implements ReplicaProbe {

  /**
   * {@code lag_ms} es 0 cuando la réplica ya aplicó todo lo recibido ({@code replay >= receive}):
   * con el primario sin escrituras, el instante de la última transacción aplicada no avanza (o es
   * nulo si no se aplicó ninguna desde el {@code pg_basebackup}) y daría un lag falso. Se usa
   * {@code >=} y no {@code =}: al reiniciarse el receptor de WAL, {@code receive_lsn} vuelve al
   * inicio del segmento y queda por detrás de {@code replay_lsn}.
   */
  static final String FITNESS_QUERY =
      """
      SELECT pg_is_in_recovery() AS in_recovery,
             (SELECT status FROM pg_stat_wal_receiver LIMIT 1) AS receiver_status,
             CASE
               WHEN NOT pg_is_in_recovery() THEN NULL
               WHEN pg_last_wal_replay_lsn() >= pg_last_wal_receive_lsn() THEN 0
               ELSE (EXTRACT(EPOCH FROM now() - pg_last_xact_replay_timestamp()) * 1000)::bigint
             END AS lag_ms
      """;

  private static final String STREAMING = "streaming";

  private final Map<DbNode, DataSource> probePools;
  private final int queryTimeoutSeconds;
  private final long maxLagMs;

  /**
   * Crea el sondeo.
   *
   * @param probePools pools exclusivos de sondeo por réplica.
   * @param properties tiempos de sondeo y lag máximo.
   */
  public PostgresReplicaProbe(ReplicaDataSources probePools, ReplicaHealthProperties properties) {
    this.probePools = probePools.asMap();
    this.queryTimeoutSeconds = (int) Math.max(1, ceilSeconds(properties.probeTimeout()));
    this.maxLagMs = properties.maxLag().toMillis();
  }

  @Override
  public ReplicaStatus probe(DbNode replica) {
    DataSource pool = probePools.get(replica);
    if (pool == null) {
      return ReplicaStatus.down("unknown-replica");
    }
    try (Connection connection = pool.getConnection();
        Statement statement = connection.createStatement()) {
      statement.setQueryTimeout(queryTimeoutSeconds);
      try (ResultSet rs = statement.executeQuery(FITNESS_QUERY)) {
        rs.next();
        long lag = rs.getLong("lag_ms");
        OptionalLong lagMs = rs.wasNull() ? OptionalLong.empty() : OptionalLong.of(lag);
        return classify(
            rs.getBoolean("in_recovery"), rs.getString("receiver_status"), lagMs, maxLagMs);
      }
    } catch (SQLException | RuntimeException ex) {
      return ReplicaStatus.down(describe(ex));
    }
  }

  /**
   * Clasifica el resultado de la consulta de aptitud.
   *
   * @param inRecovery la instancia opera como réplica.
   * @param receiverStatus estado del receptor de WAL, o {@code null} si no hay.
   * @param lagMs retraso medido.
   * @param maxLagMs retraso máximo aceptado.
   * @return estado de la réplica.
   */
  static ReplicaStatus classify(
      boolean inRecovery, String receiverStatus, OptionalLong lagMs, long maxLagMs) {
    if (!inRecovery) {
      return ReplicaStatus.lagging(lagMs, "not-in-recovery");
    }
    if (!STREAMING.equals(receiverStatus)) {
      return ReplicaStatus.lagging(
          lagMs, "wal-receiver-" + (receiverStatus == null ? "absent" : receiverStatus));
    }
    if (lagMs.isEmpty()) {
      return ReplicaStatus.lagging(lagMs, "lag-unknown");
    }
    if (lagMs.getAsLong() > maxLagMs) {
      return ReplicaStatus.lagging(lagMs, "lag-exceeded");
    }
    return ReplicaStatus.up(lagMs.getAsLong());
  }

  private static String describe(Exception ex) {
    String state = ex instanceof SQLException sql ? sql.getSQLState() : null;
    return ex.getClass().getSimpleName() + (state == null ? "" : "(" + state + ")");
  }

  private static long ceilSeconds(Duration duration) {
    return (duration.toMillis() + 999) / 1000;
  }
}
