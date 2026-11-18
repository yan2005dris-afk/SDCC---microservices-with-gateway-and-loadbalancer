package com.distribuidos.gateway.controller;

import java.time.OffsetDateTime;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/fallback")
public class FallbackController {

  private static final Logger log = LoggerFactory.getLogger(FallbackController.class);

  @RequestMapping("/orders")
  public Mono<ResponseEntity<Map<String, Object>>> orderFallback() {
    log.warn("[CIRCUIT BREAKER TRIGGERED] Fallback activado para order-service");
    Map<String, Object> response =
        Map.of(
            "status", HttpStatus.SERVICE_UNAVAILABLE.value(),
            "error", "Service Unavailable",
            "service", "order-service",
            "message",
                "El servicio de órdenes no está disponible o ha superado el umbral de errores/timeout. El Circuit Breaker ha interceptado la llamada para proteger el sistema.",
            "timestamp", OffsetDateTime.now().toString());
    return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response));
  }

  @RequestMapping("/inventory")
  public Mono<ResponseEntity<Map<String, Object>>> inventoryFallback() {
    log.warn("[CIRCUIT BREAKER TRIGGERED] Fallback activado para inventory-service");
    Map<String, Object> response =
        Map.of(
            "status", HttpStatus.SERVICE_UNAVAILABLE.value(),
            "error", "Service Unavailable",
            "service", "inventory-service",
            "message",
                "El servicio de inventario no está disponible en este momento. Intente de nuevo más tarde.",
            "timestamp", OffsetDateTime.now().toString());
    return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response));
  }
}
