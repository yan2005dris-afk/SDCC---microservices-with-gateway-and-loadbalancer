package com.distribuidos.gateway.loadbalancer;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propiedades fuertemente tipadas para el módulo de balanceo en el API Gateway.
 *
 * @param healthCheckInterval intervalo entre verificaciones de salud de instancias.
 * @param healthCheckPath ruta HTTP consultada en cada instancia para health check.
 * @param cacheTtl tiempo de vida de la caché de instancias descubiertas.
 */
@ConfigurationProperties(prefix = "sdcc.loadbalancer")
public record LoadBalancerClientProperties(
    Duration healthCheckInterval, String healthCheckPath, Duration cacheTtl) {

  private static final Duration DEFAULT_INTERVAL = Duration.ofSeconds(5);
  private static final String DEFAULT_HEALTH_PATH = "/actuator/health";

  /** Constructor compacto que garantiza valores por defecto seguros ante configuración omitida. */
  public LoadBalancerClientProperties {
    healthCheckInterval = healthCheckInterval != null ? healthCheckInterval : DEFAULT_INTERVAL;
    healthCheckPath =
        (healthCheckPath != null && !healthCheckPath.isBlank())
            ? healthCheckPath
            : DEFAULT_HEALTH_PATH;
    cacheTtl = cacheTtl != null ? cacheTtl : DEFAULT_INTERVAL;
  }
}
