package com.distribuidos.order.datasource.health;

import com.distribuidos.order.datasource.HealthAwareReplicaSelector;
import com.distribuidos.order.datasource.ReplicaDataSources;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;

/** Componentes del sondeo de salud de réplicas y del selector que lo usa. */
@Configuration
public class ReplicaHealthConfig {

  /**
   * Registro de estado de las réplicas.
   *
   * @param replicas pools de tráfico (define las réplicas y su orden).
   * @param properties configuración del sondeo.
   * @return registro.
   */
  @Bean
  public ReplicaHealthRegistry replicaHealthRegistry(
      @Qualifier("replicaDataSources") ReplicaDataSources replicas,
      ReplicaHealthProperties properties) {
    return new ReplicaHealthRegistry(replicas.nodes(), properties.rejoinThreshold());
  }

  /**
   * Sondeo de aptitud PostgreSQL.
   *
   * @param probePools pools exclusivos de sondeo.
   * @param properties configuración del sondeo.
   * @return sondeo.
   */
  @Bean
  public ReplicaProbe replicaProbe(
      @Qualifier("replicaProbeDataSources") ReplicaDataSources probePools,
      ReplicaHealthProperties properties) {
    return new PostgresReplicaProbe(probePools, properties);
  }

  /**
   * Monitor periódico de todas las réplicas.
   *
   * @param probe sondeo.
   * @param registry registro.
   * @param taskScheduler planificador de Spring.
   * @param properties configuración del sondeo.
   * @return monitor.
   */
  @Bean
  public ReplicaHealthMonitor replicaHealthMonitor(
      ReplicaProbe probe,
      ReplicaHealthRegistry registry,
      TaskScheduler taskScheduler,
      ReplicaHealthProperties properties) {
    return new ReplicaHealthMonitor(probe, registry, taskScheduler, properties);
  }

  /**
   * Selector de réplicas basado en el registro.
   *
   * @param registry registro.
   * @return selector.
   */
  @Bean
  public HealthAwareReplicaSelector replicaSelector(ReplicaHealthRegistry registry) {
    return new HealthAwareReplicaSelector(registry);
  }
}
