package com.distribuidos.order.datasource;

import static org.assertj.core.api.Assertions.assertThat;

import com.distribuidos.order.datasource.health.ReplicaHealthRegistry;
import com.distribuidos.order.datasource.health.ReplicaStatus;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class HealthAwareReplicaSelectorTest {

  private static final DbNode R1 = new DbNode("REPLICA_1");
  private static final DbNode R2 = new DbNode("REPLICA_2");
  private static final DbNode R3 = new DbNode("REPLICA_3");

  private final ReplicaHealthRegistry registry = new ReplicaHealthRegistry(List.of(R1, R2, R3), 1);
  private final HealthAwareReplicaSelector selector = new HealthAwareReplicaSelector(registry);

  @Test
  void rotatesAcrossAllReplicasInRotation() {
    assertThat(pick(6)).containsExactly(R1, R2, R3, R1, R2, R3);
    assertThat(selector.replicaCount()).isEqualTo(3);
  }

  @Test
  void skipsReplicasOutOfRotation() {
    registry.record(R2, ReplicaStatus.down("connection-refused"));

    assertThat(pick(4)).doesNotContain(R2).contains(R1, R3);
  }

  @Test
  void markDownRemovesReplicaImmediately() {
    selector.markDown(R1);

    assertThat(registry.isInRotation(R1)).isFalse();
    assertThat(pick(4)).doesNotContain(R1);
  }

  @Test
  void returnsEmptyWhenNoReplicaIsInRotation() {
    List.of(R1, R2, R3).forEach(selector::markDown);

    assertThat(selector.next()).isEmpty();
    assertThat(selector.replicaCount()).isEqualTo(3);
  }

  private List<DbNode> pick(int times) {
    List<DbNode> picked = new ArrayList<>();
    for (int i = 0; i < times; i++) {
      picked.add(selector.next().orElseThrow());
    }
    return picked;
  }
}
