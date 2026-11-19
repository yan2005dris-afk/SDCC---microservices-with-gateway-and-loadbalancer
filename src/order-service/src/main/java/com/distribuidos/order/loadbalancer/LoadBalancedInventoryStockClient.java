package com.distribuidos.order.loadbalancer;

import com.distribuidos.order.loadbalancer.dto.InventoryReserveRequest;
import com.distribuidos.order.loadbalancer.dto.InventoryReserveResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * Implementación HTTP balanceada de {@link InventoryStockClient} respaldada por Spring Cloud
 * LoadBalancer y descubrimiento lógico en Eureka.
 */
@Component
public class LoadBalancedInventoryStockClient implements InventoryStockClient {

  private static final Logger log = LoggerFactory.getLogger(LoadBalancedInventoryStockClient.class);
  private static final String RESERVE_ENDPOINT = "/api/inventory/reserve";

  private final RestClient restClient;

  /**
   * Construye el cliente HTTP con balanceo client-side sobre el nombre lógico configurado.
   *
   * @param restClientBuilder constructor de {@link RestClient} anotado con {@link LoadBalanced}.
   * @param inventoryBaseUrl URL lógica o base para {@code inventory-service}.
   */
  public LoadBalancedInventoryStockClient(
      @LoadBalanced RestClient.Builder restClientBuilder,
      @Value("${sdcc.loadbalancer.inventory-base-url:http://inventory-service}")
          String inventoryBaseUrl) {
    this.restClient = restClientBuilder.baseUrl(inventoryBaseUrl).build();
  }

  @Override
  public InventoryReserveResponse reserveStock(InventoryReserveRequest request) {
    try {
      InventoryReserveResponse response =
          restClient
              .post()
              .uri(RESERVE_ENDPOINT)
              .body(request)
              .retrieve()
              .body(InventoryReserveResponse.class);
      return response != null
          ? response
          : InventoryReserveResponse.unavailable(
              request.productCode(), "Respuesta vacía de inventory-service");
    } catch (HttpClientErrorException.BadRequest ex) {
      log.warn("Reserva rechazada por inventory-service para {}", request.productCode());
      return extractBadRequestBody(request.productCode(), ex);
    }
  }

  private InventoryReserveResponse extractBadRequestBody(
      String productCode, HttpClientErrorException.BadRequest ex) {
    InventoryReserveResponse body = ex.getResponseBodyAs(InventoryReserveResponse.class);
    return body != null
        ? body
        : InventoryReserveResponse.unavailable(productCode, "Stock insuficiente");
  }
}
