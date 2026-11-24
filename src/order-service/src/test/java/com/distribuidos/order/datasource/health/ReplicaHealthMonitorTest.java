package com.distribuidos.order.datasource.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.distribuidos.order.datasource.DbNode;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TaskScheduler;

class ReplicaHealthMonitorTest {

  private static final DbNode R1 = new DbNode("REPLICA_1");
  private static final DbNode R2 = new DbNode("REPLICA_2");
  private static final ReplicaHealthProperties PROPS =
      new ReplicaHealthProperties(
          Duration.ofSeconds(2), Duration.ofMillis(100), Duration.ofSeconds(5), 1);

  private final ReplicaHealthRegistry registry = new ReplicaHealthRegistry(List.of(R1, R2), 1);
  private final TaskScheduler scheduler = mock(TaskScheduler.class);

  @Test
  void probesEveryReplicaIncludingHealthyOnes() {
    ReplicaProbe probe =
        node -> node.equals(R1) ? ReplicaStatus.down("connection-refused") : ReplicaStatus.up(5);

    new ReplicaHealthMonitor(probe, registry, scheduler, PROPS).probeAll();

    assertThat(registry.isInRotation(R1)).isFalse();
    assertThat(registry.isInRotation(R2)).isTrue();
    assertThat(registry.lagMs(R2)).hasValue(5);
  }

  @Test
  void probeThatExceedsTheDeadlineCountsAsDown() {
    ReplicaProbe probe =
        node -> {
          if (node.equals(R1)) {
            sleep(2_000);
          }
          return ReplicaStatus.up(0);
        };

    long start = System.nanoTime();
    new ReplicaHealthMonitor(probe, registry, scheduler, PROPS).probeAll();

    assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(1));
    assertThat(registry.snapshot().get(0).lastStatus().orElseThrow().reason())
        .isEqualTo("probe-timeout");
    assertThat(registry.isInRotation(R2)).isTrue();
  }

  @Test
  void lifecycleSchedulesWithConfiguredIntervalAndStops() {
    ReplicaHealthMonitor monitor =
        new ReplicaHealthMonitor(node -> ReplicaStatus.up(0), registry, scheduler, PROPS);

    monitor.start();

    verify(scheduler).scheduleWithFixedDelay(any(Runnable.class), eq(Duration.ofSeconds(2)));
    monitor.stop();
    assertThat(monitor.isRunning()).isFalse();
  }

  private static void sleep(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
    }
  }
}
