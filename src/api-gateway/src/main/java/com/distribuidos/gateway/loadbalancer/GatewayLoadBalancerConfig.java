package com.distribuidos.gateway.loadbalancer;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Configuración global de Spring Cloud LoadBalancer para el API Gateway. */
@Configuration
@EnableConfigurationProperties(LoadBalancerClientProperties.class)
@LoadBalancerClients(defaultConfiguration = ServiceLoadBalancerConfiguration.class)
public class GatewayLoadBalancerConfig {

  /**
   * Registra el listener de telemetría para auditar cada decisión de balanceo.
   *
   * @return instancia única de {@link LoadBalancerTelemetryLifecycle}.
   */
  @Bean
  public LoadBalancerTelemetryLifecycle loadBalancerTelemetryLifecycle() {
    return new LoadBalancerTelemetryLifecycle();
  }
}
