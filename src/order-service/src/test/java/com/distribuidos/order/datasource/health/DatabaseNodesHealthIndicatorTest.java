package com.distribuidos.order.datasource.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.distribuidos.order.datasource.DbNode;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

class DatabaseNodesHealthIndicatorTest {

  private static final DbNode R1 = new DbNode("REPLICA_1");
  private static final DbNode R2 = new DbNode("REPLICA_2");

  private final ReplicaHealthRegistry registry = new ReplicaHealthRegistry(List.of(R1, R2), 2);

  @Test
  void oneReplicaDownIsUpButDegraded() throws SQLException {
    registry.record(R1, ReplicaStatus.down("connection-refused"));
    registry.record(R2, ReplicaStatus.up(10));

    Health health = new DatabaseNodesHealthIndicator(primary(true), registry).health();

    assertThat(health.getStatus()).isEqualTo(Status.UP);
    assertThat(health.getDetails()).containsEntry("degraded", true);
    assertThat(replica(health, "REPLICA_1"))
        .containsEntry("state", "DOWN")
        .containsEntry("inRotation", false);
    assertThat(replica(health, "REPLICA_2"))
        .containsEntry("state", "UP")
        .containsEntry("lagMs", 10L);
  }

  @Test
  void primaryDownIsDown() throws SQLException {
    Health health = new DatabaseNodesHealthIndicator(primary(false), registry).health();

    assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    assertThat(health.getDetails()).containsEntry("primary", "DOWN");
    assertThat(replica(health, "REPLICA_1")).containsEntry("state", "UNKNOWN");
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> replica(Health health, String name) {
    return (Map<String, Object>)
        ((Map<String, Object>) health.getDetails().get("replicas")).get(name);
  }

  private static DataSource primary(boolean up) throws SQLException {
    DataSource dataSource = mock(DataSource.class);
    if (up) {
      Connection connection = mock(Connection.class);
      when(connection.isValid(1)).thenReturn(true);
      when(dataSource.getConnection()).thenReturn(connection);
    } else {
      when(dataSource.getConnection()).thenThrow(new SQLException("down", "08001"));
    }
    return dataSource;
  }
}
