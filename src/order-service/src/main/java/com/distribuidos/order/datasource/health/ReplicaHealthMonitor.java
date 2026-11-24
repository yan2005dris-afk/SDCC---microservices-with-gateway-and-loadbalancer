package com.distribuidos.order.datasource.health;

import com.distribuidos.order.datasource.DbNode;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.context.SmartLifecycle;
import org.springframework.scheduling.TaskScheduler;

/**
 * Sondea periódicamente <b>todas</b> las réplicas (también las sanas, ORD-05) y registra el
 * resultado en {@link ReplicaHealthRegistry}.
 *
 * <p>Los sondeos de un ciclo corren en paralelo con hilos virtuales, así que la duración del ciclo
 * no crece con el número de réplicas. Un sondeo que no termina a tiempo cuenta como {@code DOWN}.
 */
public class ReplicaHealthMonitor implements SmartLifecycle {

  /** Margen sobre {@code probe-timeout} para esperar un sondeo antes de darlo por perdido. */
  private static final Duration PROBE_GRACE = Duration.ofMillis(500);

  private final ReplicaProbe probe;
  private final ReplicaHealthRegistry registry;
  private final TaskScheduler scheduler;
  private final Duration interval;
  private final Duration probeDeadline;
  private final ExecutorService probeExecutor = Executors.newVirtualThreadPerTaskExecutor();
  private volatile ScheduledFuture<?> schedule;

  /**
   * Crea el monitor.
   *
   * @param probe sondeo de aptitud.
   * @param registry registro de estado.
   * @param scheduler planificador de Spring.
   * @param properties intervalo y tiempo de sondeo.
   */
  public ReplicaHealthMonitor(
      ReplicaProbe probe,
      ReplicaHealthRegistry registry,
      TaskScheduler scheduler,
      ReplicaHealthProperties properties) {
    this.probe = probe;
    this.registry = registry;
    this.scheduler = scheduler;
    this.interval = properties.interval();
    this.probeDeadline = properties.probeTimeout().plus(PROBE_GRACE);
  }

  /** Ejecuta un ciclo de sondeo sobre todas las réplicas. */
  public void probeAll() {
    List<DbNode> replicas = registry.replicas();
    List<Future<ReplicaStatus>> results =
        replicas.stream().map(node -> probeExecutor.submit(() -> probe.probe(node))).toList();
    for (int i = 0; i < replicas.size(); i++) {
      registry.record(replicas.get(i), await(results.get(i)));
    }
  }

  private ReplicaStatus await(Future<ReplicaStatus> result) {
    try {
      return result.get(probeDeadline.toMillis(), TimeUnit.MILLISECONDS);
    } catch (TimeoutException ex) {
      result.cancel(true);
      return ReplicaStatus.down("probe-timeout");
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      return ReplicaStatus.down("probe-interrupted");
    } catch (ExecutionException ex) {
      return ReplicaStatus.down("probe-error:" + ex.getCause().getClass().getSimpleName());
    }
  }

  @Override
  public void start() {
    if (schedule == null) {
      schedule = scheduler.scheduleWithFixedDelay(this::probeAll, interval);
    }
  }

  @Override
  public void stop() {
    ScheduledFuture<?> current = schedule;
    if (current != null) {
      current.cancel(false);
      schedule = null;
    }
    probeExecutor.shutdownNow();
  }

  @Override
  public boolean isRunning() {
    return schedule != null;
  }
}
