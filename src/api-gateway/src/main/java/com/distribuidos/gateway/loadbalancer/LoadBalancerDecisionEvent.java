package com.distribuidos.gateway.loadbalancer;

import org.springframework.cloud.client.ServiceInstance;

/**
 * Evento inmutable de trazabilidad que describe una instancia seleccionada por el balanceador.
 *
 * @param serviceId nombre lógico del microservicio destino.
 * @param instanceId identificador único de la instancia seleccionada.
 * @param host dirección de red de la instancia.
 * @param port puerto expuesto de la instancia.
 * @param status estado del ciclo de vida del balanceo.
 */
public record LoadBalancerDecisionEvent(
    String serviceId, String instanceId, String host, int port, String status) {

  /**
   * Construye un evento de decisión a partir de una instancia descubierta por Spring Cloud.
   *
   * @param instance instancia destino seleccionada; puede ser nula en descartes tempranos.
   * @param status estado de la decisión (por ejemplo {@code SELECTED}, {@code SUCCESS}).
   * @return evento tipado listo para registro estructurado.
   */
  public static LoadBalancerDecisionEvent from(ServiceInstance instance, String status) {
    if (instance == null) {
      return new LoadBalancerDecisionEvent("unknown", "none", "unknown", 0, status);
    }
    String resolvedInstanceId =
        instance.getInstanceId() != null ? instance.getInstanceId() : instance.getHost();
    return new LoadBalancerDecisionEvent(
        instance.getServiceId(),
        resolvedInstanceId,
        instance.getHost(),
        instance.getPort(),
        status);
  }
}
