package com.distribuidos.order.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.appinfo.InstanceInfo.InstanceStatus;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.web.context.WebServerGracefulShutdownLifecycle;

/** Pruebas del drenaje previo al apagado (salida del tráfico vía Eureka). */
class DiscoveryDrainLifecycleTest {

  @Test
  void shouldMarkInstanceDownAndWaitBeforeWebServerShutdown() throws InterruptedException {
    ApplicationInfoManager manager = mock(ApplicationInfoManager.class);
    List<Long> sleeps = new ArrayList<>();
    DiscoveryDrainLifecycle lifecycle =
        new DiscoveryDrainLifecycle(provider(manager), Duration.ofSeconds(10), sleeps::add);
    lifecycle.start();

    lifecycle.stop();

    verify(manager).setInstanceStatus(InstanceStatus.DOWN);
    assertThat(sleeps).containsExactly(10_000L);
    assertThat(lifecycle.isRunning()).isFalse();
  }

  @Test
  void shouldMarkDownBeforeWaiting() throws InterruptedException {
    ApplicationInfoManager manager = mock(ApplicationInfoManager.class);
    DiscoveryDrainLifecycle.Sleeper sleeper = mock(DiscoveryDrainLifecycle.Sleeper.class);
    DiscoveryDrainLifecycle lifecycle =
        new DiscoveryDrainLifecycle(provider(manager), Duration.ofSeconds(1), sleeper);

    lifecycle.stop();

    InOrder order = inOrder(manager, sleeper);
    order.verify(manager).setInstanceStatus(InstanceStatus.DOWN);
    order.verify(sleeper).sleep(1_000L);
  }

  @Test
  void shouldSkipWhenDelayIsZero() {
    ApplicationInfoManager manager = mock(ApplicationInfoManager.class);
    List<Long> sleeps = new ArrayList<>();
    DiscoveryDrainLifecycle lifecycle =
        new DiscoveryDrainLifecycle(provider(manager), Duration.ZERO, sleeps::add);

    lifecycle.stop();

    verifyNoInteractions(manager);
    assertThat(sleeps).isEmpty();
  }

  @Test
  void shouldSkipWhenEurekaIsNotAvailable() {
    List<Long> sleeps = new ArrayList<>();
    DiscoveryDrainLifecycle lifecycle =
        new DiscoveryDrainLifecycle(provider(null), Duration.ofSeconds(10), sleeps::add);

    lifecycle.stop();

    assertThat(sleeps).isEmpty();
    assertThat(lifecycle.isRunning()).isFalse();
  }

  @Test
  void shouldRestoreInterruptFlagWhenWaitIsInterrupted() {
    ApplicationInfoManager manager = mock(ApplicationInfoManager.class);
    DiscoveryDrainLifecycle lifecycle =
        new DiscoveryDrainLifecycle(
            provider(manager),
            Duration.ofSeconds(10),
            millis -> {
              throw new InterruptedException("test");
            });

    lifecycle.stop();

    assertThat(Thread.interrupted()).isTrue();
    assertThat(lifecycle.isRunning()).isFalse();
  }

  @Test
  void shouldStopBeforeGracefulWebServerShutdown() {
    DiscoveryDrainLifecycle lifecycle =
        new DiscoveryDrainLifecycle(provider(null), Duration.ZERO, millis -> {});

    assertThat(lifecycle.getPhase())
        .isGreaterThan(WebServerGracefulShutdownLifecycle.SMART_LIFECYCLE_PHASE);
    assertThat(lifecycle.isAutoStartup()).isTrue();
  }

  @SuppressWarnings("unchecked")
  private static ObjectProvider<ApplicationInfoManager> provider(ApplicationInfoManager manager) {
    ObjectProvider<ApplicationInfoManager> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(manager);
    return provider;
  }
}
