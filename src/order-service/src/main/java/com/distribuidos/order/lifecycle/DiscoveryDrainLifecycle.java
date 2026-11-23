package com.distribuidos.order.lifecycle;

import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.appinfo.InstanceInfo.InstanceStatus;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/**
 * Saca la instancia del tráfico antes de apagarla.
 *
 * <p>Al recibir SIGTERM, Spring detiene los {@link SmartLifecycle} de mayor fase primero. Este
 * componente usa la fase máxima, así que se ejecuta <b>antes</b> del apagado ordenado del servidor
 * web ({@code server.shutdown: graceful}). Marca la instancia como {@code DOWN} en Eureka y espera
 * {@code sdcc.shutdown.drain-delay} para que el Gateway refresque su registro y deje de enviarle
 * peticiones nuevas. Durante la espera la instancia sigue atendiendo con normalidad; después,
 * Tomcat drena las peticiones en curso.
 *
 * <p>Se cambia el estado <b>local</b> de la instancia, no un estado sobrescrito en el servidor de
 * Eureka, para que al reiniciar el contenedor vuelva a registrarse como {@code UP}.
 */
@Component
public class DiscoveryDrainLifecycle implements SmartLifecycle {

  private static final Logger log = LoggerFactory.getLogger(DiscoveryDrainLifecycle.class);

  private final ObjectProvider<ApplicationInfoManager> applicationInfoManager;
  private final Duration drainDelay;
  private final Sleeper sleeper;
  private volatile boolean running;

  /**
   * Crea el componente con la espera configurada.
   *
   * @param applicationInfoManager estado local de la instancia en Eureka (ausente si Eureka está
   *     deshabilitado).
   * @param drainDelay tiempo de espera tras marcar {@code DOWN}; {@code 0s} lo desactiva.
   */
  @Autowired
  public DiscoveryDrainLifecycle(
      ObjectProvider<ApplicationInfoManager> applicationInfoManager,
      @Value("${sdcc.shutdown.drain-delay:15s}") Duration drainDelay) {
    this(applicationInfoManager, drainDelay, millis -> Thread.sleep(millis));
  }

  DiscoveryDrainLifecycle(
      ObjectProvider<ApplicationInfoManager> applicationInfoManager,
      Duration drainDelay,
      Sleeper sleeper) {
    this.applicationInfoManager = applicationInfoManager;
    this.drainDelay = drainDelay;
    this.sleeper = sleeper;
  }

  @Override
  public void start() {
    running = true;
  }

  @Override
  public void stop() {
    try {
      ApplicationInfoManager manager = applicationInfoManager.getIfAvailable();
      if (manager == null || drainDelay.isZero() || drainDelay.isNegative()) {
        log.info("shutdown.drain skipped reason={}", manager == null ? "no-eureka" : "delay=0");
        return;
      }
      manager.setInstanceStatus(InstanceStatus.DOWN);
      log.info(
          "shutdown.drain status=DOWN delayMs={} (esperando a que el Gateway deje de enrutar)",
          drainDelay.toMillis());
      sleeper.sleep(drainDelay.toMillis());
      log.info("shutdown.drain completed; iniciando apagado ordenado del servidor web");
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      log.warn("shutdown.drain interrupted");
    } finally {
      running = false;
    }
  }

  @Override
  public boolean isRunning() {
    return running;
  }

  /**
   * Fase máxima: se detiene antes que el apagado ordenado del servidor web, cuya fase es menor.
   *
   * @return {@link Integer#MAX_VALUE}.
   */
  @Override
  public int getPhase() {
    return Integer.MAX_VALUE;
  }

  /** Abstracción de la espera para poder probar el componente sin dormir el hilo. */
  @FunctionalInterface
  interface Sleeper {
    void sleep(long millis) throws InterruptedException;
  }
}
