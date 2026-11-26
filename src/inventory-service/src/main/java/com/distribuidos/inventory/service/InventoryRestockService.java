package com.distribuidos.inventory.service;

import com.distribuidos.inventory.dto.RestockResponse;
import com.distribuidos.inventory.exception.ProductNotFoundException;
import com.distribuidos.inventory.model.InventoryProduct;
import com.distribuidos.inventory.model.MovementType;
import com.distribuidos.inventory.repository.InventoryProductRepository;
import com.distribuidos.inventory.repository.StockMovementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Atomically adds stock and records the corresponding RESTOCK movement. */
@Service
public class InventoryRestockService {

  private final InventoryProductRepository repository;
  private final StockMovementRepository stockMovementRepository;

  public InventoryRestockService(
      InventoryProductRepository repository, StockMovementRepository stockMovementRepository) {
    this.repository = repository;
    this.stockMovementRepository = stockMovementRepository;
  }

  @Transactional
  public RestockResponse restockProduct(String productCode, int quantity) {
    int updated = repository.restockAtomically(productCode, quantity);
    if (updated == 0) {
      throw notFound(productCode);
    }

    stockMovementRepository.insertIfAbsent(
        productCode, MovementType.RESTOCK.name(), quantity, null);

    InventoryProduct product =
        repository.findByProductCode(productCode).orElseThrow(() -> notFound(productCode));
    return new RestockResponse(
        productCode, quantity, product.getStock(), "Stock reabastecido exitosamente");
  }

  private static ProductNotFoundException notFound(String productCode) {
    return new ProductNotFoundException(productCode, "Producto " + productCode + " no encontrado");
  }
}
