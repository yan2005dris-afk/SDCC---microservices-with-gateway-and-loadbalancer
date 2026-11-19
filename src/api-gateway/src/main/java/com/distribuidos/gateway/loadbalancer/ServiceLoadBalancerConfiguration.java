package com.distribuidos.gateway.loadbalancer;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.loadbalancer.core.ReactorLoadBalancer;
import org.springframework.cloud.loadbalancer.core.RoundRobinLoadBalancer;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.cloud.loadbalancer.support.LoadBalancerClientFactory;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * Configuración por cliente de Spring Cloud LoadBalancer con Round-Robin y health checks activos.
 *
 * <p>No lleva {@code @Configuration} para mantenerse aislada en cada contexto hijo de {@code
 * LoadBalancerClientFactory}.
 */
public class ServiceLoadBalancerConfiguration {

  /**
   * Configura el algoritmo Round-Robin explícito para repartir solicitudes equitativamente.
   *
   * @param environment entorno del cliente de balanceo actual.
   * @param loadBalancerClientFactory fábrica de clientes de balanceo.
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

  /**
   * Construye la cadena de descubrimiento con health checks activos y caché local.
   *
   * @param context contexto de aplicación configurable del cliente LoadBalancer.
   * @return proveedor de instancias saludables para el servicio destino.
   */
  @Bean
  public ServiceInstanceListSupplier discoveryClientServiceInstanceListSupplier(
      ConfigurableApplicationContext context) {
    return ServiceInstanceListSupplier.builder()
        .withDiscoveryClient()
        .withHealthChecks()
        .withCaching()
        .build(context);
  }
}
