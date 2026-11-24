package com.distribuidos.inventory.service;

import com.distribuidos.inventory.exception.ProductNotFoundException;
import com.distribuidos.inventory.exception.StockUnavailableException;
import com.distribuidos.inventory.model.InventoryProduct;
import com.distribuidos.inventory.repository.InventoryProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reserves stock through a single conditional UPDATE, avoiding read-modify-write conflicts. */
@Service
public class InventoryReservationService {

  private final InventoryProductRepository repository;

  public InventoryReservationService(InventoryProductRepository repository) {
    this.repository = repository;
  }

  /** Atomically reserves {@code quantity} units and returns the remaining stock. */
  @Transactional
  public int reserveStock(String productCode, int quantity) {
    int updated = repository.reserveAtomically(productCode, quantity);
    if (updated == 0) {
      InventoryProduct product =
          repository
              .findByProductCode(productCode)
              .orElseThrow(() -> new ProductNotFoundException(productCode));
      throw new StockUnavailableException(productCode, product.getStock());
    }
    return repository
        .findByProductCode(productCode)
        .orElseThrow(() -> new ProductNotFoundException(productCode))
        .getStock();
  }
}
