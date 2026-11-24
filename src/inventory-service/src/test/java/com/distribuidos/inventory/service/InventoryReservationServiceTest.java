package com.distribuidos.inventory.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.distribuidos.inventory.dto.ReservationResponse;
import com.distribuidos.inventory.exception.ProductNotFoundException;
import com.distribuidos.inventory.exception.ReservationNotFoundException;
import com.distribuidos.inventory.exception.StockUnavailableException;
import com.distribuidos.inventory.model.InventoryProduct;
import com.distribuidos.inventory.model.MovementType;
import com.distribuidos.inventory.model.StockMovement;
import com.distribuidos.inventory.repository.InventoryProductRepository;
import com.distribuidos.inventory.repository.StockMovementRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit tests for the atomic reservation flow. Real database concurrency belongs to B4-06. */
@ExtendWith(MockitoExtension.class)
class InventoryReservationServiceTest {

  @Mock private InventoryProductRepository repository;

  @Mock private StockMovementRepository stockMovementRepository;

  @Test
  void successfulReserveReturnsRemainingStockWithoutEntitySave() {
    when(repository.reserveAtomically("PROD-001", 5)).thenReturn(1);
    when(repository.findByProductCode("PROD-001")).thenReturn(Optional.of(product(45)));

    ReservationResponse response = service().reserveStock("PROD-001", 5, null);

    assertThat(response.productCode()).isEqualTo("PROD-001");
    assertThat(response.available()).isTrue();
    assertThat(response.remainingStock()).isEqualTo(45);
    assertThat(response.unitPrice()).isEqualByComparingTo("1899.99");
    assertThat(response.productName()).isEqualTo("Laptop Dell XPS 15");
    assertThat(response.message()).isEqualTo("Reserva confirmada");
    verify(repository).reserveAtomically("PROD-001", 5);
    verify(repository, never()).save(any());
    verify(stockMovementRepository).save(any(StockMovement.class));
  }

  @Test
  void reserveRecordsReservationMovementWithOrderNumber() {
    when(repository.reserveAtomically("PROD-001", 5)).thenReturn(1);
    when(repository.findByProductCode("PROD-001")).thenReturn(Optional.of(product(45)));

    service().reserveStock("PROD-001", 5, "ORD-001");

    ArgumentCaptor<StockMovement> captor = ArgumentCaptor.forClass(StockMovement.class);
    verify(stockMovementRepository).save(captor.capture());
    StockMovement movement = captor.getValue();
    assertThat(movement.getProductCode()).isEqualTo("PROD-001");
    assertThat(movement.getType()).isEqualTo(MovementType.RESERVATION);
    assertThat(movement.getQuantity()).isEqualTo(5);
    assertThat(movement.getReferenceOrderNumber()).isEqualTo("ORD-001");
  }

  @Test
  void repeatedReserveWithSameOrderNumberDoesNotDecrementStockAgain() {
    when(stockMovementRepository.findByReferenceOrderNumberAndType(
            "ORD-001", MovementType.RESERVATION))
        .thenReturn(Optional.of(movement(MovementType.RESERVATION, 5, "ORD-001")));
    when(repository.findByProductCode("PROD-001")).thenReturn(Optional.of(product(45)));

    ReservationResponse response = service().reserveStock("PROD-001", 5, "ORD-001");

    assertThat(response.remainingStock()).isEqualTo(45);
    verify(repository, never()).reserveAtomically(anyString(), anyInt());
    verify(stockMovementRepository, never()).save(any());
  }

  @Test
  void releaseRestoresStockAndRecordsReleaseMovement() {
    when(stockMovementRepository.findByReferenceOrderNumberAndType(
            "ORD-001", MovementType.RESERVATION))
        .thenReturn(Optional.of(movement(MovementType.RESERVATION, 5, "ORD-001")));
    when(repository.releaseAtomically("PROD-001", 5)).thenReturn(1);

    Map<String, Object> response = service().releaseStock("ORD-001");

    assertThat(response).containsEntry("productCode", "PROD-001");
    assertThat(response).containsEntry("quantity", 5);
    assertThat(response).containsEntry("message", "Stock liberado exitosamente");
    verify(repository).releaseAtomically("PROD-001", 5);

    ArgumentCaptor<StockMovement> captor = ArgumentCaptor.forClass(StockMovement.class);
    verify(stockMovementRepository).save(captor.capture());
    StockMovement movement = captor.getValue();
    assertThat(movement.getType()).isEqualTo(MovementType.RELEASE);
    assertThat(movement.getProductCode()).isEqualTo("PROD-001");
    assertThat(movement.getQuantity()).isEqualTo(5);
    assertThat(movement.getReferenceOrderNumber()).isEqualTo("ORD-001");
  }

  @Test
  void repeatedReleaseDoesNotRestoreStockAgain() {
    when(stockMovementRepository.findByReferenceOrderNumberAndType(
            "ORD-001", MovementType.RESERVATION))
        .thenReturn(Optional.of(movement(MovementType.RESERVATION, 5, "ORD-001")));
    when(stockMovementRepository.existsByReferenceOrderNumberAndType(
            "ORD-001", MovementType.RELEASE))
        .thenReturn(true);

    Map<String, Object> response = service().releaseStock("ORD-001");

    assertThat(response).containsEntry("productCode", "PROD-001");
    assertThat(response).containsEntry("quantity", 5);
    verify(repository, never()).releaseAtomically(anyString(), anyInt());
    verify(stockMovementRepository, never()).save(any());
  }

  @Test
  void releaseWithoutReservationThrowsReservationNotFound() {
    assertThrows(ReservationNotFoundException.class, () -> service().releaseStock("ORD-999"));
  }

  @Test
  void zeroUpdatedRowsWithMissingProductThrowsProductNotFound() {
    when(repository.reserveAtomically("PROD-999", 1)).thenReturn(0);
    when(repository.findByProductCode("PROD-999")).thenReturn(Optional.empty());

    assertThrows(ProductNotFoundException.class, () -> service().reserveStock("PROD-999", 1, null));
  }

  @Test
  void zeroUpdatedRowsWithStockThrowsStockUnavailableWithAvailableStock() {
    when(repository.reserveAtomically("PROD-001", 5)).thenReturn(0);
    when(repository.findByProductCode("PROD-001")).thenReturn(Optional.of(product(2)));

    StockUnavailableException exception =
        assertThrows(
            StockUnavailableException.class, () -> service().reserveStock("PROD-001", 5, null));

    assertThat(exception.getProductCode()).isEqualTo("PROD-001");
    assertThat(exception.getAvailableStock()).isEqualTo(2);
  }

  @Test
  void twentyConcurrentSingleUnitReservesAllSucceedAndDrainStock() throws Exception {
    AtomicInteger stock = new AtomicInteger(20);
    when(repository.reserveAtomically(eq("PROD-001"), eq(1)))
        .thenAnswer(
            invocation -> {
              int previous = stock.getAndUpdate(current -> current > 0 ? current - 1 : current);
              return previous > 0 ? 1 : 0;
            });
    when(repository.findByProductCode("PROD-001"))
        .thenAnswer(invocation -> Optional.of(product(stock.get())));

    ExecutorService pool = Executors.newFixedThreadPool(20);
    CountDownLatch start = new CountDownLatch(1);
    List<Future<ReservationResponse>> futures = new ArrayList<>();
    try {
      for (int i = 0; i < 20; i++) {
        futures.add(
            pool.submit(
                () -> {
                  start.await();
                  return service().reserveStock("PROD-001", 1, null);
                }));
      }
      start.countDown();

      for (Future<ReservationResponse> future : futures) {
        assertThat(future.get(10, TimeUnit.SECONDS).remainingStock()).isGreaterThanOrEqualTo(0);
      }
      assertThat(futures).hasSize(20);
      assertThat(stock.get()).isZero();
    } finally {
      pool.shutdownNow();
      assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }
  }

  private InventoryReservationService service() {
    return new InventoryReservationService(repository, stockMovementRepository);
  }

  private static StockMovement movement(MovementType type, int quantity, String orderNumber) {
    StockMovement movement = new StockMovement();
    movement.setProductCode("PROD-001");
    movement.setType(type);
    movement.setQuantity(quantity);
    movement.setReferenceOrderNumber(orderNumber);
    return movement;
  }

  private static InventoryProduct product(int stock) {
    InventoryProduct product = new InventoryProduct();
    product.setProductCode("PROD-001");
    product.setStock(stock);
    product.setReservedStock(0);
    product.setName("Laptop Dell XPS 15");
    product.setUnitPrice(new BigDecimal("1899.99"));
    return product;
  }
}
