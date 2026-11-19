package com.distribuidos.order.loadbalancer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.CompletionContext;
import org.springframework.cloud.client.loadbalancer.LoadBalancerLifecycle;
import org.springframework.cloud.client.loadbalancer.Request;
import org.springframework.cloud.client.loadbalancer.Response;

/**
 * Listener de ciclo de vida para auditar el balanceo RPC saliente desde {@code order-service} hacia
 * otros microservicios descubiertos por Eureka.
 */
public class OrderLoadBalancerLifecycle
    implements LoadBalancerLifecycle<Object, Object, ServiceInstance> {

  private static final Logger log = LoggerFactory.getLogger(OrderLoadBalancerLifecycle.class);

  @Override
  public boolean supports(Class requestType, Class responseType, Class serverType) {
    return ServiceInstance.class.isAssignableFrom(serverType);
  }

  @Override
  public void onStart(Request<Object> request) {
    log.debug("[RPC LOADBALANCER] Iniciando resolución de instancia destino");
  }

  @Override
  public void onStartRequest(Request<Object> request, Response<ServiceInstance> lbResponse) {
    logSelection(lbResponse);
  }

  @Override
  public void onComplete(CompletionContext<Object, ServiceInstance, Object> completionContext) {
    logCompletion(completionContext);
  }

  private void logSelection(Response<ServiceInstance> lbResponse) {
    if (lbResponse == null || !lbResponse.hasServer()) {
      log.warn("[RPC LOADBALANCER] No hay instancias disponibles para atender la llamada");
      return;
    }
    ServiceInstance server = lbResponse.getServer();
    log.info(
        "[RPC LOADBALANCER] Target: {} -> Instance: {} ({}:{})",
        server.getServiceId(),
        server.getInstanceId(),
        server.getHost(),
        server.getPort());
  }

  private void logCompletion(CompletionContext<Object, ServiceInstance, Object> context) {
    if (context == null) {
      return;
    }
    log.debug("[RPC LOADBALANCER] Llamada finalizada con estado {}", context.status());
  }
}
