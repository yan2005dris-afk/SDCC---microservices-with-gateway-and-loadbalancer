package com.distribuidos.order.datasource.health;

import static org.assertj.core.api.Assertions.assertThat;

import com.distribuidos.order.datasource.DbNode;
import java.util.List;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;

class ReplicaHealthRegistryTest {

  private static final DbNode R1 = new DbNode("REPLICA_1");
  private static final DbNode R2 = new DbNode("REPLICA_2");

  private final ReplicaHealthRegistry registry = new ReplicaHealthRegistry(List.of(R1, R2), 2);

  @Test
  void replicasStartInRotationWithUnknownStatus() {
    assertThat(registry.isInRotation(R1)).isTrue();
    assertThat(registry.snapshot())
        .allSatisfy(s -> assertThat(s.lastStatus()).isEmpty())
        .extracting(ReplicaStatusView.Snapshot::node)
        .containsExactly(R1, R2);
  }

  @Test
  void singleFailureRemovesReplicaAndRejoinNeedsConsecutiveUpProbes() {
    registry.record(R1, ReplicaStatus.down("connection-refused"));
    assertThat(registry.isInRotation(R1)).isFalse();

    registry.record(R1, ReplicaStatus.up(0));
    assertThat(registry.isInRotation(R1)).isFalse();

    registry.record(R1, ReplicaStatus.up(0));
    assertThat(registry.isInRotation(R1)).isTrue();
    assertThat(registry.isInRotation(R2)).isTrue();
  }

  @Test
  void nonUpResultResetsTheRejoinCounter() {
    registry.markDown(R1, "request-connection-failure");
    registry.record(R1, ReplicaStatus.up(0));
    registry.record(R1, ReplicaStatus.lagging(OptionalLong.of(9000), "lag-exceeded"));
    registry.record(R1, ReplicaStatus.up(0));

    assertThat(registry.isInRotation(R1)).isFalse();
  }

  @Test
  void laggingReplicaLeavesRotationAndExposesItsLag() {
    registry.record(R2, ReplicaStatus.lagging(OptionalLong.of(9000), "lag-exceeded"));

    assertThat(registry.isInRotation(R2)).isFalse();
    assertThat(registry.lagMs(R2)).hasValue(9000);
  }

  @Test
  void upProbeKeepsReplicaInRotationAndUpdatesLag() {
    registry.record(R1, ReplicaStatus.up(120));

    assertThat(registry.isInRotation(R1)).isTrue();
    assertThat(registry.lagMs(R1)).hasValue(120);
  }

  @Test
  void unknownReplicaIsIgnored() {
    DbNode other = new DbNode("REPLICA_9");
    registry.record(other, ReplicaStatus.down("x"));

    assertThat(registry.isInRotation(other)).isFalse();
    assertThat(registry.lagMs(other)).isEmpty();
  }
}
