package com.distribuidos.gateway.loadbalancer;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.CompletionContext;
import org.springframework.cloud.client.loadbalancer.DefaultRequest;
import org.springframework.cloud.client.loadbalancer.DefaultResponse;
import org.springframework.cloud.client.loadbalancer.EmptyResponse;
import org.springframework.cloud.client.loadbalancer.Response;
import org.springframework.cloud.loadbalancer.core.RoundRobinLoadBalancer;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import reactor.core.publisher.Flux;

/** Pruebas unitarias del balanceo Round-Robin (P02/P03) y telemetría en el Gateway. */
class RoundRobinLoadBalancerDistributionTest {

  private static final String ORDER_SERVICE = "order-service";

  @Test
  void shouldDistribute100RequestsEvenlyBetweenTwoInstances() {
    ServiceInstance instance1 = instance("order-service-1", "10.0.0.1", 8081);
    ServiceInstance instance2 = instance("order-service-2", "10.0.0.2", 8083);
    RoundRobinLoadBalancer loadBalancer = createLoadBalancer(List.of(instance1, instance2));

    Map<String, Integer> hits = new HashMap<>();
    for (int i = 0; i < 100; i++) {
      Response<ServiceInstance> response = loadBalancer.choose(new DefaultRequest<>()).block();
      assertThat(response).isNotNull();
      assertThat(response.hasServer()).isTrue();
      hits.merge(response.getServer().getInstanceId(), 1, Integer::sum);
    }

    assertThat(hits).containsEntry("order-service-1", 50);
    assertThat(hits).containsEntry("order-service-2", 50);
  }

  @Test
  void shouldRouteAllTrafficToSurvivingInstanceWhenOneFails() {
    ServiceInstance surviving = instance("order-service-2", "10.0.0.2", 8083);
    RoundRobinLoadBalancer loadBalancer = createLoadBalancer(List.of(surviving));

    for (int i = 0; i < 20; i++) {
      Response<ServiceInstance> response = loadBalancer.choose(new DefaultRequest<>()).block();
      assertThat(response).isNotNull();
      assertThat(response.hasServer()).isTrue();
      assertThat(response.getServer().getInstanceId()).isEqualTo("order-service-2");
    }
  }

  @Test
  void shouldApplyDefaultPropertiesAndRecordTelemetryLifecycle() {
    LoadBalancerClientProperties props = new LoadBalancerClientProperties(null, " ", null);
    assertThat(props.healthCheckInterval()).isEqualTo(Duration.ofSeconds(5));
    assertThat(props.healthCheckPath()).isEqualTo("/actuator/health");
    assertThat(props.cacheTtl()).isEqualTo(Duration.ofSeconds(5));

    ServiceInstance instance = instance("order-service-1", "localhost", 8081);
    LoadBalancerDecisionEvent event = LoadBalancerDecisionEvent.from(instance, "SELECTED");
    assertThat(event.serviceId()).isEqualTo(ORDER_SERVICE);
    assertThat(event.instanceId()).isEqualTo("order-service-1");
    assertThat(event.port()).isEqualTo(8081);

    LoadBalancerTelemetryLifecycle lifecycle = new LoadBalancerTelemetryLifecycle();
    assertThat(lifecycle.supports(Object.class, Object.class, ServiceInstance.class)).isTrue();
    DefaultRequest<Object> request = new DefaultRequest<>();
    DefaultResponse lbResponse = new DefaultResponse(instance);
    lifecycle.onStart(request);
    lifecycle.onStartRequest(request, lbResponse);
    lifecycle.onStartRequest(request, new EmptyResponse());
    lifecycle.onComplete(
        new CompletionContext<>(CompletionContext.Status.SUCCESS, request, lbResponse));
  }

  private ServiceInstance instance(String instanceId, String host, int port) {
    return new DefaultServiceInstance(instanceId, ORDER_SERVICE, host, port, false);
  }

  private RoundRobinLoadBalancer createLoadBalancer(List<ServiceInstance> instances) {
    ServiceInstanceListSupplier supplier =
        new ServiceInstanceListSupplier() {
          @Override
          public String getServiceId() {
            return ORDER_SERVICE;
          }

          @Override
          public Flux<List<ServiceInstance>> get() {
            return Flux.just(instances);
          }
        };
    return new RoundRobinLoadBalancer(providerFor(supplier), ORDER_SERVICE);
  }

  private ObjectProvider<ServiceInstanceListSupplier> providerFor(
      ServiceInstanceListSupplier supplier) {
    return new ObjectProvider<>() {
      @Override
      public ServiceInstanceListSupplier getObject(Object... args) throws BeansException {
        return supplier;
      }

      @Override
      public ServiceInstanceListSupplier getIfAvailable() throws BeansException {
        return supplier;
      }

      @Override
      public ServiceInstanceListSupplier getIfAvailable(
          Supplier<ServiceInstanceListSupplier> defaultSupplier) throws BeansException {
        return supplier;
      }

      @Override
      public ServiceInstanceListSupplier getIfUnique() throws BeansException {
        return supplier;
      }

      @Override
      public ServiceInstanceListSupplier getObject() throws BeansException {
        return supplier;
      }
    };
  }
}
