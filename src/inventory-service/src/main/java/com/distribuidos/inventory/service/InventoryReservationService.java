package com.distribuidos.inventory.service;

import com.distribuidos.inventory.dto.ReservationResponse;
import com.distribuidos.inventory.exception.InvalidStockReleaseException;
import com.distribuidos.inventory.exception.ProductNotFoundException;
import com.distribuidos.inventory.exception.ReservationNotFoundException;
import com.distribuidos.inventory.exception.StockUnavailableException;
import com.distribuidos.inventory.model.InventoryProduct;
import com.distribuidos.inventory.model.MovementType;
import com.distribuidos.inventory.model.StockMovement;
import com.distribuidos.inventory.repository.InventoryProductRepository;
import com.distribuidos.inventory.repository.StockMovementRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reserves stock with a three-layer guarantee chain:
 *
 * <ol>
 *   <li>the product row is locked with {@code FOR UPDATE} before any idempotency check;
 *   <li>the movement is inserted before the stock update with {@code ON CONFLICT DO NOTHING}, so a
 *       duplicate request never reaches the update;
 *   <li>the partial unique index on {@code (reference_order_number, movement_type, product_code)}
 *       is the database-level safety net.
 * </ol>
 */
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
    boolean idempotent = hasOrderNumber(orderNumber);
    if (idempotent) {
      lockProduct(productCode);
      if (findReservation(orderNumber, productCode).isPresent()) {
        return reservationResponse(productCode);
      }
    }

    int inserted = insertMovement(productCode, MovementType.RESERVATION, quantity, orderNumber);
    if (inserted == 0) {
      // The unique index has already recorded the winner. Re-read it and leave stock untouched.
      findReservation(orderNumber, productCode)
          .orElseThrow(
              () -> new IllegalStateException("Conflicting reservation movement not found"));
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

    return reservationResponse(productCode);
  }

  /** Releases a previously reserved stock quantity; repeated calls return the same response. */
  @Transactional
  public Map<String, Object> releaseStock(String orderNumber) {
    List<StockMovement> reservations =
        stockMovementRepository.findAllByReferenceOrderNumberAndType(
            orderNumber, MovementType.RESERVATION);
    if (reservations.isEmpty()) {
      throw new ReservationNotFoundException(orderNumber);
    }

    List<Map<String, Object>> releases = new ArrayList<>();
    for (StockMovement reservation : reservations) {
      String productCode = reservation.getProductCode();
      int quantity = reservation.getQuantity();
      lockProduct(productCode);

      int inserted = insertMovement(productCode, MovementType.RELEASE, quantity, orderNumber);
      if (inserted == 0) {
        releases.add(releaseResponse(productCode, quantity));
        continue;
      }

      int updated = repository.releaseAtomically(productCode, quantity);
      if (updated == 0) {
        throw new InvalidStockReleaseException(productCode);
      }
      releases.add(releaseResponse(productCode, quantity));
    }

    Map<String, Object> response = new LinkedHashMap<>(releases.get(0));
    response.put("orderNumber", orderNumber);
    response.put("releases", releases);
    return response;
  }

  private boolean hasOrderNumber(String orderNumber) {
    return orderNumber != null && !orderNumber.isBlank();
  }

  private void lockProduct(String productCode) {
    repository
        .findByProductCodeForUpdate(productCode)
        .orElseThrow(() -> new ProductNotFoundException(productCode));
  }

  private int insertMovement(
      String productCode, MovementType type, int quantity, String orderNumber) {
    return stockMovementRepository.insertIfAbsent(productCode, type.name(), quantity, orderNumber);
  }

  private Optional<StockMovement> findReservation(String orderNumber, String productCode) {
    return stockMovementRepository.findByReferenceOrderNumberAndTypeAndProductCode(
        orderNumber, MovementType.RESERVATION, productCode);
  }

  private Map<String, Object> releaseResponse(String productCode, int quantity) {
    return Map.of(
        "productCode", productCode, "quantity", quantity, "message", "Stock liberado exitosamente");
  }

  private ReservationResponse reservationResponse(String productCode) {
    // This read must remain inside the @Transactional method that owns the row lock.
    // remainingStock means "stock after my reservation", not stock at response time.
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
