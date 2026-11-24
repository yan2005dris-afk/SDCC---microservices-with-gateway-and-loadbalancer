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
import org.springframework.dao.DataIntegrityViolationException;
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
    boolean idempotent = hasOrderNumber(orderNumber);
    if (idempotent) {
      repository
          .findByProductCodeForUpdate(productCode)
          .orElseThrow(() -> new ProductNotFoundException(productCode));
      if (findReservation(orderNumber, productCode).isPresent()) {
        return reservationResponse(productCode);
      }
    }

    try {
      int updated = repository.reserveAtomically(productCode, quantity);
      if (updated == 0) {
        InventoryProduct product =
            repository
                .findByProductCode(productCode)
                .orElseThrow(() -> new ProductNotFoundException(productCode));
        throw new StockUnavailableException(productCode, product.getStock());
      }
      saveMovement(productCode, MovementType.RESERVATION, quantity, orderNumber);
    } catch (DataIntegrityViolationException exception) {
      if (!idempotent || findReservation(orderNumber, productCode).isEmpty()) {
        throw exception;
      }
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
      repository
          .findByProductCodeForUpdate(productCode)
          .orElseThrow(() -> new ProductNotFoundException(productCode));

      if (stockMovementRepository.existsByReferenceOrderNumberAndTypeAndProductCode(
          orderNumber, MovementType.RELEASE, productCode)) {
        releases.add(releaseResponse(productCode, quantity));
        continue;
      }

      int updated = repository.releaseAtomically(productCode, quantity);
      if (updated == 0) {
        throw new InvalidStockReleaseException(productCode);
      }

      try {
        saveMovement(productCode, MovementType.RELEASE, quantity, orderNumber);
      } catch (DataIntegrityViolationException exception) {
        if (!stockMovementRepository.existsByReferenceOrderNumberAndTypeAndProductCode(
            orderNumber, MovementType.RELEASE, productCode)) {
          throw exception;
        }
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

  private Optional<StockMovement> findReservation(String orderNumber, String productCode) {
    return stockMovementRepository.findByReferenceOrderNumberAndTypeAndProductCode(
        orderNumber, MovementType.RESERVATION, productCode);
  }

  private void saveMovement(
      String productCode, MovementType type, int quantity, String orderNumber) {
    StockMovement movement = new StockMovement();
    movement.setProductCode(productCode);
    movement.setType(type);
    movement.setQuantity(quantity);
    movement.setReferenceOrderNumber(orderNumber);
    stockMovementRepository.save(movement);
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
