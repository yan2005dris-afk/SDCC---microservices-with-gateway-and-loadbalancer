package com.distribuidos.order.controller;

import com.distribuidos.order.datasource.ReadNodesUnavailableException;
import com.distribuidos.order.dto.ApiErrorResponse;
import com.distribuidos.order.observability.InstanceIdentity;
import com.distribuidos.order.service.OrderRejectedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

  private ResponseEntity<ApiErrorResponse> error(HttpStatus status, String message) {
    return ResponseEntity.status(status)
        .header(OrderController.HEADER_INSTANCE_ID, instanceIdentity.id())
        .body(new ApiErrorResponse(message));
  }
}
