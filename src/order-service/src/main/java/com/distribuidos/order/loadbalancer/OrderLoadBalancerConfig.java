package com.distribuidos.order.loadbalancer;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/** Configuración de infraestructura client-side LoadBalancer para {@code order-service}. */
@Configuration
@LoadBalancerClients(defaultConfiguration = OrderServiceLoadBalancerClientConfig.class)
public class OrderLoadBalancerConfig {

  /**
   * Expone el constructor de {@link RestClient} habilitado para resolución lógica vía Eureka.
   *
   * @return constructor balanceado de {@link RestClient}.
   */
  @Bean
  @LoadBalanced
  public RestClient.Builder loadBalancedRestClientBuilder() {
    return RestClient.builder();
  }

  /**
   * Registra la telemetría de balanceo RPC saliente.
   *
   * @return instancia de {@link OrderLoadBalancerLifecycle}.
   */
  @Bean
  public OrderLoadBalancerLifecycle orderLoadBalancerLifecycle() {
    return new OrderLoadBalancerLifecycle();
  }
}
