package com.distribuidos.order.controller;

import com.distribuidos.order.datasource.ReadNodesUnavailableException;
import com.distribuidos.order.dto.ApiErrorResponse;
import com.distribuidos.order.loadbalancer.InventoryContractException;
import com.distribuidos.order.loadbalancer.InventoryUnavailableException;
import com.distribuidos.order.loadbalancer.ProductNotFoundException;
import com.distribuidos.order.loadbalancer.StockUnavailableException;
import com.distribuidos.order.observability.InstanceIdentity;
import com.distribuidos.order.service.OrderPersistenceException;
import com.distribuidos.order.service.OrderRejectedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Traduce las excepciones de dominio e infraestructura a respuestas HTTP con el formato de error
 * existente ({@link ApiErrorResponse}), sin exponer trazas al cliente.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

  private static final String RETRY_AFTER_SECONDS = "2";

  private final InstanceIdentity instanceIdentity;

  /**
   * Crea el manejador.
   *
   * @param instanceIdentity identidad de esta instancia.
   */
  public ApiExceptionHandler(InstanceIdentity instanceIdentity) {
    this.instanceIdentity = instanceIdentity;
  }

  /**
   * Pedido rechazado por datos o por inventario: {@code 400}, como antes del refactor.
   *
   * @param ex motivo del rechazo.
   * @return respuesta {@code 400}.
   */
  @ExceptionHandler(OrderRejectedException.class)
  public ResponseEntity<ApiErrorResponse> handleRejected(OrderRejectedException ex) {
    return error(HttpStatus.BAD_REQUEST, ex.getMessage());
  }

  /**
   * Ningún nodo pudo atender la lectura: {@code 503}, para que el cliente pueda reintentar.
   *
   * @param ex fallo de disponibilidad.
   * @return respuesta {@code 503}.
   */
  @ExceptionHandler(ReadNodesUnavailableException.class)
  public ResponseEntity<ApiErrorResponse> handleReadUnavailable(ReadNodesUnavailableException ex) {
    log.error("db.route op=READ result=UNAVAILABLE", ex);
    return error(
        HttpStatus.SERVICE_UNAVAILABLE, "Base de datos no disponible para lectura; reintente");
  }

  /**
   * Stock insuficiente: {@code 400}, como define el PRD.
   *
   * @param ex excepción.
   * @return respuesta de error.
   */
  @ExceptionHandler(StockUnavailableException.class)
  public ResponseEntity<ApiErrorResponse> handleStockUnavailable(StockUnavailableException ex) {
    return error(HttpStatus.BAD_REQUEST, "Stock insuficiente");
  }

  /**
   * Producto inexistente: {@code 422}. El recurso {@code /orders} existe; lo que no es procesable es
   * la referencia al producto.
   *
   * @param ex excepción.
   * @return respuesta de error.
   */
  @ExceptionHandler(ProductNotFoundException.class)
  public ResponseEntity<ApiErrorResponse> handleProductNotFound(ProductNotFoundException ex) {
    return error(HttpStatus.UNPROCESSABLE_ENTITY, "Producto no existe en inventario");
  }

  /**
   * Inventario no disponible, o el pedido no se pudo registrar: {@code 503} con {@code
   * Retry-After}. Con {@code Idempotency-Key} el reintento es seguro.
   *
   * @param ex excepción.
   * @return respuesta de error.
   */
  @ExceptionHandler({InventoryUnavailableException.class, OrderPersistenceException.class})
  public ResponseEntity<ApiErrorResponse> handleTemporarilyUnavailable(RuntimeException ex) {
    log.error("order.create result=UNAVAILABLE", ex);
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .header(HttpHeaders.RETRY_AFTER, RETRY_AFTER_SECONDS)
        .header(OrderController.HEADER_INSTANCE_ID, instanceIdentity.id())
        .body(new ApiErrorResponse("Servicio temporalmente no disponible; reintente"));
  }

  /**
   * Inventario rechazó la petición de {@code order-service}: {@code 502}, error de integración.
   *
   * @param ex excepción.
   * @return respuesta de error.
   */
  @ExceptionHandler(InventoryContractException.class)
  public ResponseEntity<ApiErrorResponse> handleContract(InventoryContractException ex) {
    log.error("order.create result=INVENTORY_CONTRACT_ERROR", ex);
    return error(HttpStatus.BAD_GATEWAY, "Respuesta inválida de inventario");
  }

  private ResponseEntity<ApiErrorResponse> error(HttpStatus status, String message) {
    return ResponseEntity.status(status)
        .header(OrderController.HEADER_INSTANCE_ID, instanceIdentity.id())
        .body(new ApiErrorResponse(message));
  }
}
