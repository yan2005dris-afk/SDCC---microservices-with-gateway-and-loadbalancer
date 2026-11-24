package com.distribuidos.order.datasource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RoundRobinReplicaSelectorTest {

  private DataSource replica1;
  private DataSource replica2;
  private RoundRobinReplicaSelector selector;

  @BeforeEach
  void setUp() {
    replica1 = mock(DataSource.class);
    replica2 = mock(DataSource.class);
    Map<DataSourceType, DataSource> replicas = new LinkedHashMap<>();
    replicas.put(DataSourceType.REPLICA_1, replica1);
    replicas.put(DataSourceType.REPLICA_2, replica2);
    selector = new RoundRobinReplicaSelector(replicas);
  }

  @Test
  void shouldAlternateBetweenHealthyReplicas() {
    List<DataSourceType> picks = new ArrayList<>();
    for (int i = 0; i < 4; i++) {
      picks.add(selector.next().orElseThrow());
    }

    assertThat(picks)
        .containsExactly(
            DataSourceType.REPLICA_1,
            DataSourceType.REPLICA_2,
            DataSourceType.REPLICA_1,
            DataSourceType.REPLICA_2);
    assertThat(selector.replicaCount()).isEqualTo(2);
  }

  @Test
  void shouldExcludeReplicaMarkedDown() {
    selector.markDown(DataSourceType.REPLICA_1);

    for (int i = 0; i < 3; i++) {
      assertThat(selector.next()).contains(DataSourceType.REPLICA_2);
    }
    assertThat(selector.isHealthy(DataSourceType.REPLICA_1)).isFalse();
  }

  @Test
  void shouldReturnEmptyWhenNoReplicaIsHealthy() {
    selector.markDown(DataSourceType.REPLICA_1);
    selector.markDown(DataSourceType.REPLICA_2);

    assertThat(selector.next()).isEqualTo(Optional.empty());
  }

  @Test
  void shouldRejoinReplicaWhenConnectionIsRestored() throws SQLException {
    selector.markDown(DataSourceType.REPLICA_1);
    Connection connection = mock(Connection.class);
    when(connection.isValid(anyInt())).thenReturn(true);
    when(replica1.getConnection()).thenReturn(connection);

    selector.recoverDegradedReplicas();

    assertThat(selector.isHealthy(DataSourceType.REPLICA_1)).isTrue();
  }

  @Test
  void shouldKeepReplicaDownWhileConnectionFails() throws SQLException {
    selector.markDown(DataSourceType.REPLICA_1);
    when(replica1.getConnection()).thenThrow(new SQLException("refused", "08001"));

    selector.recoverDegradedReplicas();

    assertThat(selector.isHealthy(DataSourceType.REPLICA_1)).isFalse();
  }

  @Test
  void shouldRequireAtLeastOneReplica() {
    assertThatThrownBy(() -> new RoundRobinReplicaSelector(Map.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
