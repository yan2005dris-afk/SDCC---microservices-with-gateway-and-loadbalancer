package com.distribuidos.inventory.controller;

import com.distribuidos.inventory.model.InventoryProduct;
import com.distribuidos.inventory.repository.InventoryProductRepository;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

  private final InventoryProductRepository repository;
  private final String instanceId;

  public InventoryController(
      InventoryProductRepository repository,
      @Value("${INSTANCE_ID:${spring.application.name}:8082}") String instanceId) {
    this.repository = repository;
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
  @Transactional
  public ResponseEntity<?> reserveStock(@RequestBody Map<String, Object> request) {
    String productCode = (String) request.get("productCode");
    Integer quantity = (Integer) request.get("quantity");

    if (productCode == null || quantity == null || quantity <= 0) {
      return ResponseEntity.badRequest().body(Map.of("error", "Parámetros inválidos"));
    }

    var productOpt = repository.findByProductCode(productCode);
    if (productOpt.isEmpty()) {
      return ResponseEntity.badRequest().body(Map.of("error", "Producto no encontrado"));
    }

    InventoryProduct product = productOpt.get();
    if (product.getStock() < quantity) {
      return ResponseEntity.badRequest()
          .body(
              Map.of(
                  "available",
                  false,
                  "error",
                  "Stock insuficiente",
                  "currentStock",
                  product.getStock()));
    }

    product.setStock(product.getStock() - quantity);
    product.setReservedStock(product.getReservedStock() + quantity);
    repository.save(product);

    return ResponseEntity.ok()
        .header("X-Instance-Id", instanceId)
        .body(
            Map.of(
                "productCode",
                productCode,
                "available",
                true,
                "remainingStock",
                product.getStock()));
  }
}
