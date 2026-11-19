package com.distribuidos.order.loadbalancer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.distribuidos.order.dto.CreateOrderRequest;
import com.distribuidos.order.loadbalancer.dto.InventoryReserveRequest;
import com.distribuidos.order.loadbalancer.dto.InventoryReserveResponse;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.CompletionContext;
import org.springframework.cloud.client.loadbalancer.DefaultRequest;
import org.springframework.cloud.client.loadbalancer.DefaultResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Pruebas unitarias del cliente RPC balanceado y los contratos tipados de {@code order-service}.
 */
class LoadBalancedInventoryStockClientTest {

  private MockRestServiceServer mockServer;
  private LoadBalancedInventoryStockClient stockClient;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder();
    this.mockServer = MockRestServiceServer.bindTo(builder).build();
    this.stockClient = new LoadBalancedInventoryStockClient(builder, "http://inventory-service");
  }

  @Test
  void shouldReserveStockSuccessfullyWhenInventoryAvailable() {
    String responseJson = "{\"productCode\":\"PROD-001\",\"available\":true,\"remainingStock\":48}";
    mockServer
        .expect(requestTo("http://inventory-service/api/inventory/reserve"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(content().json("{\"productCode\":\"PROD-001\",\"quantity\":2}"))
        .andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON));

    InventoryReserveResponse response =
        stockClient.reserveStock(new InventoryReserveRequest("PROD-001", 2));

    assertThat(response.isAvailable()).isTrue();
    assertThat(response.productCode()).isEqualTo("PROD-001");
    assertThat(response.remainingStock()).isEqualTo(48);
    mockServer.verify();
  }

  @Test
  void shouldReturnUnavailableWhenInventoryReturnsBadRequest() {
    String errorJson =
        "{\"productCode\":\"PROD-001\",\"available\":false,\"error\":\"Stock insuficiente\"}";
    mockServer
        .expect(requestTo("http://inventory-service/api/inventory/reserve"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withBadRequest().contentType(MediaType.APPLICATION_JSON).body(errorJson));

    InventoryReserveResponse response =
        stockClient.reserveStock(new InventoryReserveRequest("PROD-001", 999));

    assertThat(response.isAvailable()).isFalse();
    assertThat(response.error()).isEqualTo("Stock insuficiente");
    mockServer.verify();
  }

  @Test
  void shouldValidateStrongTypedRequestsAndLifecycle() {
    assertThatThrownBy(() -> new InventoryReserveRequest(" ", 1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new InventoryReserveRequest("PROD-001", 0))
        .isInstanceOf(IllegalArgumentException.class);

    CreateOrderRequest valid =
        new CreateOrderRequest(null, "", "PROD-001", 2, new BigDecimal("19.99"));
    assertThat(valid.hasRequiredFields()).isTrue();
    assertThat(valid.resolvedCustomerName()).isEqualTo("Cliente General");
    assertThat(valid.resolvedCustomerEmail()).isEqualTo("cliente@distribuidos.com");

    OrderLoadBalancerLifecycle lifecycle = new OrderLoadBalancerLifecycle();
    assertThat(lifecycle.supports(Object.class, Object.class, ServiceInstance.class)).isTrue();
    DefaultRequest<Object> request = new DefaultRequest<>();
    DefaultResponse serverResponse =
        new DefaultResponse(
            new DefaultServiceInstance("inv-1", "inventory-service", "10.0.0.3", 8082, false));
    lifecycle.onStart(request);
    lifecycle.onStartRequest(request, serverResponse);
    lifecycle.onComplete(
        new CompletionContext<>(CompletionContext.Status.SUCCESS, request, serverResponse));
  }
}
