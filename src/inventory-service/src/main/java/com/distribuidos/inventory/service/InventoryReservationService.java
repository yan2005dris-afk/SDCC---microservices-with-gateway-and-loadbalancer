package com.distribuidos.inventory.service;

import com.distribuidos.inventory.dto.ReservationResponse;
import com.distribuidos.inventory.exception.ProductNotFoundException;
import com.distribuidos.inventory.exception.ReservationNotFoundException;
import com.distribuidos.inventory.exception.StockUnavailableException;
import com.distribuidos.inventory.model.InventoryProduct;
import com.distribuidos.inventory.model.MovementType;
import com.distribuidos.inventory.model.StockMovement;
import com.distribuidos.inventory.repository.InventoryProductRepository;
import com.distribuidos.inventory.repository.StockMovementRepository;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reserves stock through a single conditional UPDATE, avoiding read-modify-write conflicts. */
@Service
public class InventoryReservationService {

  private final InventoryProductRepository repository;
  private final StockMovementRepository stockMovementRepository;

  public InventoryReservationService(
      InventoryProductRepository repository, StockMovementRepository stockMovementRepository) {
    this.repository = repository;
    this.stockMovementRepository = stockMovementRepository;
  }

  /** Atomically reserves {@code quantity} units and returns the enriched reservation response. */
  @Transactional
  public ReservationResponse reserveStock(String productCode, int quantity, String orderNumber) {
    if (orderNumber != null
        && !orderNumber.isBlank()
        && stockMovementRepository
            .findByReferenceOrderNumberAndType(orderNumber, MovementType.RESERVATION)
            .isPresent()) {
      return reservationResponse(productCode);
    }

    int updated = repository.reserveAtomically(productCode, quantity);
    if (updated == 0) {
      InventoryProduct product =
          repository
              .findByProductCode(productCode)
              .orElseThrow(() -> new ProductNotFoundException(productCode));
      throw new StockUnavailableException(productCode, product.getStock());
    }

    StockMovement movement = new StockMovement();
    movement.setProductCode(productCode);
    movement.setType(MovementType.RESERVATION);
    movement.setQuantity(quantity);
    movement.setReferenceOrderNumber(orderNumber);
    stockMovementRepository.save(movement);

    return reservationResponse(productCode);
  }

  /** Releases a previously reserved stock quantity; repeated calls return the same response. */
  @Transactional
  public Map<String, Object> releaseStock(String orderNumber) {
    StockMovement reservation =
        stockMovementRepository
            .findByReferenceOrderNumberAndType(orderNumber, MovementType.RESERVATION)
            .orElseThrow(() -> new ReservationNotFoundException(orderNumber));

    if (stockMovementRepository.existsByReferenceOrderNumberAndType(
        orderNumber, MovementType.RELEASE)) {
      return releaseResponse(reservation.getProductCode(), reservation.getQuantity());
    }

    int updated =
        repository.releaseAtomically(reservation.getProductCode(), reservation.getQuantity());
    if (updated == 0) {
      throw new ProductNotFoundException(reservation.getProductCode());
    }

    StockMovement movement = new StockMovement();
    movement.setProductCode(reservation.getProductCode());
    movement.setType(MovementType.RELEASE);
    movement.setQuantity(reservation.getQuantity());
    movement.setReferenceOrderNumber(orderNumber);
    stockMovementRepository.save(movement);

    return releaseResponse(reservation.getProductCode(), reservation.getQuantity());
  }

  private Map<String, Object> releaseResponse(String productCode, int quantity) {
    return Map.of(
        "productCode", productCode, "quantity", quantity, "message", "Stock liberado exitosamente");
  }

  private ReservationResponse reservationResponse(String productCode) {
    InventoryProduct product =
        repository
            .findByProductCode(productCode)
            .orElseThrow(() -> new ProductNotFoundException(productCode));
    return new ReservationResponse(
        productCode,
        true,
        product.getStock(),
        product.getUnitPrice(),
        product.getName(),
        "Reserva confirmada");
  }
}
