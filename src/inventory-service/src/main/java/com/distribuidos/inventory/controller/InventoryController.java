package com.distribuidos.inventory.controller;

import com.distribuidos.inventory.dto.ReserveStockRequest;
import com.distribuidos.inventory.repository.InventoryProductRepository;
import com.distribuidos.inventory.service.InventoryReservationService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

  private final InventoryProductRepository repository;
  private final InventoryReservationService reservationService;
  private final String instanceId;

  public InventoryController(
      InventoryProductRepository repository,
      InventoryReservationService reservationService,
      @Value("${INSTANCE_ID:${spring.application.name}:8082}") String instanceId) {
    this.repository = repository;
    this.reservationService = reservationService;
    this.instanceId = instanceId;
  }

  @GetMapping("/{productCode}")
  public ResponseEntity<?> getProduct(@PathVariable String productCode) {
    return repository
        .findByProductCode(productCode)
        .map(p -> ResponseEntity.ok().header("X-Instance-Id", instanceId).body(p))
        .orElse(ResponseEntity.notFound().build());
  }

  @PostMapping("/reserve")
  public ResponseEntity<?> reserveStock(@Valid @RequestBody ReserveStockRequest request) {
    int remainingStock = reservationService.reserveStock(request.productCode(), request.quantity());

    return ResponseEntity.ok()
        .header("X-Instance-Id", instanceId)
        .body(
            Map.of(
                "productCode",
                request.productCode(),
                "available",
                true,
                "remainingStock",
                remainingStock));
  }
}
