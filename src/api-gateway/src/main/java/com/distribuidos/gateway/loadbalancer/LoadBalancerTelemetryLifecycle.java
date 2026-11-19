package com.distribuidos.gateway.loadbalancer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.CompletionContext;
import org.springframework.cloud.client.loadbalancer.LoadBalancerLifecycle;
import org.springframework.cloud.client.loadbalancer.Request;
import org.springframework.cloud.client.loadbalancer.Response;

/**
 * Lifecycle listener de Spring Cloud LoadBalancer para auditar decisiones de balanceo en Gateway.
 */
public class LoadBalancerTelemetryLifecycle
    implements LoadBalancerLifecycle<Object, Object, ServiceInstance> {

  private static final Logger log = LoggerFactory.getLogger(LoadBalancerTelemetryLifecycle.class);

  @Override
  public boolean supports(Class requestType, Class responseType, Class serverType) {
    return ServiceInstance.class.isAssignableFrom(serverType);
  }

  @Override
  public void onStart(Request<Object> request) {
    log.debug("[LOADBALANCER START] Resolviendo instancia destino");
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
      log.warn("[LOADBALANCER SELECT] Sin instancias saludables disponibles");
      return;
    }
    LoadBalancerDecisionEvent event =
        LoadBalancerDecisionEvent.from(lbResponse.getServer(), "SELECTED");
    log.info(
        "[LOADBALANCER SELECT] Service: {} -> Instance: {} ({}:{})",
        event.serviceId(),
        event.instanceId(),
        event.host(),
        event.port());
  }

  private void logCompletion(CompletionContext<Object, ServiceInstance, Object> context) {
    if (context == null) {
      return;
    }
    Response<ServiceInstance> response = context.getLoadBalancerResponse();
    ServiceInstance server =
        (response != null && response.hasServer()) ? response.getServer() : null;
    LoadBalancerDecisionEvent event =
        LoadBalancerDecisionEvent.from(server, context.status().name());
    log.debug(
        "[LOADBALANCER COMPLETE] Service: {} -> Instance: {} Status: {}",
        event.serviceId(),
        event.instanceId(),
        event.status());
  }
}
