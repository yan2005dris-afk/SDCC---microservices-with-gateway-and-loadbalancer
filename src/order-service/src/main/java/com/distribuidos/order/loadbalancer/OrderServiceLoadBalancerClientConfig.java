package com.distribuidos.order.loadbalancer;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.loadbalancer.core.ReactorLoadBalancer;
import org.springframework.cloud.loadbalancer.core.RoundRobinLoadBalancer;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.cloud.loadbalancer.support.LoadBalancerClientFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * Configuración aislada del algoritmo Round-Robin para clientes HTTP balanceados en {@code
 * order-service}.
 */
public class OrderServiceLoadBalancerClientConfig {

  /**
   * Define {@link RoundRobinLoadBalancer} para distribuir llamadas RPC entre instancias vivas.
   *
   * @param environment entorno del cliente de balanceo.
   * @param loadBalancerClientFactory fábrica de clientes de Spring Cloud LoadBalancer.
   * @return balanceador reactivo Round-Robin.
   */
  @Bean
  public ReactorLoadBalancer<ServiceInstance> reactorServiceInstanceLoadBalancer(
      Environment environment, LoadBalancerClientFactory loadBalancerClientFactory) {
    String serviceId = LoadBalancerClientFactory.getName(environment);
    return new RoundRobinLoadBalancer(
        loadBalancerClientFactory.getLazyProvider(serviceId, ServiceInstanceListSupplier.class),
        serviceId);
  }
}
