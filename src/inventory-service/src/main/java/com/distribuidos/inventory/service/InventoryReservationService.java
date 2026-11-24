package com.distribuidos.inventory.service;

import com.distribuidos.inventory.dto.ReleaseResponse;
import com.distribuidos.inventory.dto.ReleaseResponse.ReleasedItem;
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
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

  /** Log estructurado de negocio: una línea por resultado de reserva. */
  private static final Logger log = LoggerFactory.getLogger(InventoryReservationService.class);

  /** Resultado de negocio de una reserva que sí descontó stock. */
  private static final String RESULT_OK = "OK";

  /** Resultado de negocio de una reserva rechazada por stock insuficiente. */
  private static final String RESULT_INSUFFICIENT = "INSUFFICIENT";

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
      if (findReservation(orderNumber, productCode).isPresent()) {
        ReservationResponse replay = reservationResponse(productCode);
        logReserve(productCode, quantity, orderNumber, RESULT_OK, replay.remainingStock());
        return replay;
      }
    }

    int inserted = insertMovement(productCode, MovementType.RESERVATION, quantity, orderNumber);
    if (inserted == 0) {
      // The unique index has already recorded the winner. Re-read it and leave stock untouched.
      findReservation(orderNumber, productCode)
          .orElseThrow(
              () -> new IllegalStateException("Conflicting reservation movement not found"));
      ReservationResponse winner = reservationResponse(productCode);
      logReserve(productCode, quantity, orderNumber, RESULT_OK, winner.remainingStock());
      return winner;
    }

    int updated = repository.reserveAtomically(productCode, quantity);
    if (updated == 0) {
      InventoryProduct product =
          repository
              .findByProductCode(productCode)
              .orElseThrow(() -> new ProductNotFoundException(productCode));
      logReserve(productCode, quantity, orderNumber, RESULT_INSUFFICIENT, product.getStock());
      throw new StockUnavailableException(productCode, product.getStock());
    }

    ReservationResponse response = reservationResponse(productCode);
    logReserve(productCode, quantity, orderNumber, RESULT_OK, response.remainingStock());
    return response;
  }

  /** Releases a previously reserved stock quantity; repeated calls return the same response. */
  @Transactional
  public ReleaseResponse releaseStock(String orderNumber) {
    List<StockMovement> reservations =
        stockMovementRepository.findAllByReferenceOrderNumberAndType(
            orderNumber, MovementType.RESERVATION);
    if (reservations.isEmpty()) {
      throw new ReservationNotFoundException(orderNumber);
    }

    List<ReleasedItem> releases = new ArrayList<>();
    for (StockMovement reservation : reservations) {
      String productCode = reservation.getProductCode();
      int quantity = reservation.getQuantity();

      int inserted = insertMovement(productCode, MovementType.RELEASE, quantity, orderNumber);
      if (inserted == 0) {
        releases.add(ReleasedItem.released(productCode, quantity));
        continue;
      }

      int updated = repository.releaseAtomically(productCode, quantity);
      if (updated == 0) {
        throw new InvalidStockReleaseException(productCode);
      }
      releases.add(ReleasedItem.released(productCode, quantity));
    }

    return ReleaseResponse.of(orderNumber, releases);
  }

  private boolean hasOrderNumber(String orderNumber) {
    return orderNumber != null && !orderNumber.isBlank();
  }

  /**
   * Escribe la línea de negocio de la reserva.
   *
   * <p>Formato: {@code stock.reserve code=… qty=… order=… result=OK|INSUFFICIENT remaining=…}. No
   * incluye traza ni datos sensibles: solo código de producto, cantidad, orden, resultado y stock
   * disponible.
   *
   * @param productCode código del producto.
   * @param quantity unidades reservadas.
   * @param orderNumber número de orden (puede ser {@code null} si la reserva no es idempotente).
   * @param result resultado de negocio de la reserva.
   * @param remaining stock disponible tras la reserva (o disponible en el rechazo).
   */
  private void logReserve(
      String productCode, int quantity, String orderNumber, String result, Integer remaining) {
    log.info(
        "stock.reserve code={} qty={} order={} result={} remaining={}",
        productCode,
        quantity,
        orderNumber,
        result,
        remaining);
  }


  private int insertMovement(
      String productCode, MovementType type, int quantity, String orderNumber) {
    return stockMovementRepository.insertIfAbsent(productCode, type.name(), quantity, orderNumber);
  }

  private Optional<StockMovement> findReservation(String orderNumber, String productCode) {
    return stockMovementRepository.findByReferenceOrderNumberAndTypeAndProductCode(
        orderNumber, MovementType.RESERVATION, productCode);
  }


  private ReservationResponse reservationResponse(String productCode) {
    // Read inside the same transaction, right after the conditional UPDATE.
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
