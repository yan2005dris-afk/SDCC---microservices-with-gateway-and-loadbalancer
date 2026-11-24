package com.distribuidos.order.loadbalancer;

import com.distribuidos.order.loadbalancer.dto.InventoryReleaseRequest;
import com.distribuidos.order.loadbalancer.dto.InventoryReserveRequest;
import com.distribuidos.order.loadbalancer.dto.InventoryReserveResponse;
import java.net.ConnectException;
import java.net.UnknownHostException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Cliente de {@code inventory-service} balanceado por Eureka. Es el único lugar que conoce HTTP:
 * traduce cada respuesta o fallo a una excepción de dominio.
 *
 * <table>
 *   <caption>Traducción de respuestas</caption>
 *   <tr><td>200 con {@code available:true}</td><td>{@link ReservedStock}</td></tr>
 *   <tr><td>400 con {@code available:false}</td><td>{@link StockUnavailableException}</td></tr>
 *   <tr><td>404</td><td>{@link ProductNotFoundException}</td></tr>
 *   <tr><td>otro 4xx o cuerpo inválido</td><td>{@link InventoryContractException}</td></tr>
 *   <tr><td>5xx, timeout, sin instancias</td><td>{@link InventoryUnavailableException}</td></tr>
 * </table>
 */
@Component
public class LoadBalancedInventoryStockClient implements InventoryStockClient {

  private static final String RESERVE_ENDPOINT = "/api/inventory/reserve";
  private static final String RELEASE_ENDPOINT = "/api/inventory/release";
  private static final int MAX_CAUSE_DEPTH = 8;

  private final RestClient restClient;

  /**
   * Crea el cliente.
   *
   * @param inventoryRestClient cliente HTTP balanceado y con timeouts ({@link
   *     InventoryClientConfig}).
   */
  public LoadBalancedInventoryStockClient(
      @Qualifier("inventoryRestClient") RestClient inventoryRestClient) {
    this.restClient = inventoryRestClient;
  }

  @Override
  public ReservedStock reserve(String productCode, int quantity, String orderNumber) {
    InventoryReserveResponse body;
    try {
      body =
          restClient
              .post()
              .uri(RESERVE_ENDPOINT)
              .body(new InventoryReserveRequest(productCode, quantity, orderNumber))
              .retrieve()
              .body(InventoryReserveResponse.class);
    } catch (HttpClientErrorException.BadRequest ex) {
      throw rejected(productCode, ex);
    } catch (HttpClientErrorException.NotFound ex) {
      throw new ProductNotFoundException(productCode);
    } catch (RuntimeException ex) {
      throw translate("reserve", ex);
    }
    if (body == null || !body.isAvailable() || body.unitPrice() == null || body.productName() == null) {
      throw new InventoryContractException("Respuesta de reserva incompleta: " + body, null);
    }
    return new ReservedStock(productCode, body.productName(), body.unitPrice());
  }

  @Override
  public void release(String orderNumber) {
    try {
      restClient
          .post()
          .uri(RELEASE_ENDPOINT)
          .body(new InventoryReleaseRequest(orderNumber))
          .retrieve()
          .toBodilessEntity();
    } catch (HttpClientErrorException.NotFound ex) {
      // No había reserva para este pedido: no hay nada que compensar.
    } catch (RuntimeException ex) {
      throw translate("release", ex);
    }
  }

  private static RuntimeException rejected(String productCode, HttpClientErrorException ex) {
    InventoryReserveResponse body = null;
    try {
      body = ex.getResponseBodyAs(InventoryReserveResponse.class);
    } catch (RestClientException ignored) {
      // Cuerpo que no es una respuesta de reserva (p. ej. ProblemDetail de validación).
    }
    if (body != null && Boolean.FALSE.equals(body.available())) {
      return new StockUnavailableException(productCode, body.remainingStock());
    }
    return new InventoryContractException("inventory-service rechazó la reserva", ex);
  }

  private static RuntimeException translate(String operation, RuntimeException ex) {
    if (ex instanceof HttpStatusCodeException status && !status.getStatusCode().is5xxServerError()) {
      return new InventoryContractException(
          "inventory-service respondió " + status.getStatusCode() + " a " + operation, ex);
    }
    if (ex instanceof HttpStatusCodeException) {
      return new InventoryUnavailableException("inventory-service falló en " + operation, true, ex);
    }
    if (ex instanceof ResourceAccessException) {
      // Si ni siquiera conectó, la petición no llegó: no hay reserva que compensar.
      return new InventoryUnavailableException(
          "inventory-service no responde (" + operation + ")", !neverSent(ex), ex);
    }
    if (ex instanceof IllegalStateException) {
      // Spring Cloud LoadBalancer sin instancias registradas: la petición no salió.
      return new InventoryUnavailableException("Sin instancias de inventory-service", false, ex);
    }
    return new InventoryUnavailableException("Error llamando a inventory-service", true, ex);
  }

  private static boolean neverSent(Throwable error) {
    Throwable current = error;
    for (int depth = 0; current != null && depth < MAX_CAUSE_DEPTH; depth++) {
      if (current instanceof ConnectException || current instanceof UnknownHostException) {
        return true;
      }
      current = current.getCause();
    }
    return false;
  }
}
