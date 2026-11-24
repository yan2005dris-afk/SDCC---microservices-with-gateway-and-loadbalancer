package com.distribuidos.order.datasource.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.distribuidos.order.datasource.DbNode;
import com.distribuidos.order.datasource.ReplicaDataSources;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Map;
import java.util.OptionalLong;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class PostgresReplicaProbeTest {

  private static final long MAX_LAG = 5000;

  @Test
  void streamingReplicaWithinMaxLagIsUp() {
    ReplicaStatus status =
        PostgresReplicaProbe.classify(true, "streaming", OptionalLong.of(800), MAX_LAG);

    assertThat(status.state()).isEqualTo(ReplicaState.UP);
    assertThat(status.lagMs()).hasValue(800);
  }

  @Test
  void nodeNotInRecoveryIsLagging() {
    assertThat(PostgresReplicaProbe.classify(false, null, OptionalLong.empty(), MAX_LAG))
        .satisfies(s -> assertThat(s.state()).isEqualTo(ReplicaState.LAGGING))
        .satisfies(s -> assertThat(s.reason()).isEqualTo("not-in-recovery"));
  }

  @Test
  void replicaWithoutStreamingReceiverIsLagging() {
    assertThat(PostgresReplicaProbe.classify(true, null, OptionalLong.of(0), MAX_LAG).reason())
        .isEqualTo("wal-receiver-absent");
    assertThat(PostgresReplicaProbe.classify(true, "startup", OptionalLong.of(0), MAX_LAG).reason())
        .isEqualTo("wal-receiver-startup");
  }

  @Test
  void excessiveOrUnknownLagIsLagging() {
    assertThat(
            PostgresReplicaProbe.classify(true, "streaming", OptionalLong.of(9000), MAX_LAG)
                .reason())
        .isEqualTo("lag-exceeded");
    assertThat(
            PostgresReplicaProbe.classify(true, "streaming", OptionalLong.empty(), MAX_LAG)
                .reason())
        .isEqualTo("lag-unknown");
  }

  @Test
  void connectionFailureIsDownWithSqlState() throws SQLException {
    DbNode replica = new DbNode("REPLICA_1");
    DataSource pool = mock(DataSource.class);
    when(pool.getConnection()).thenThrow(new SQLException("refused", "08001"));
    PostgresReplicaProbe probe =
        new PostgresReplicaProbe(
            new ReplicaDataSources(Map.of(replica, pool)),
            new ReplicaHealthProperties(
                Duration.ofSeconds(2), Duration.ofSeconds(1), Duration.ofSeconds(5), 2));

    ReplicaStatus status = probe.probe(replica);

    assertThat(status.state()).isEqualTo(ReplicaState.DOWN);
    assertThat(status.reason()).isEqualTo("SQLException(08001)");
    assertThat(probe.probe(new DbNode("REPLICA_9")).reason()).isEqualTo("unknown-replica");
  }
}
