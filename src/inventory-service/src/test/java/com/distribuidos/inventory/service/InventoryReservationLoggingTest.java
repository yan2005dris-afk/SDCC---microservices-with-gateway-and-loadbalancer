package com.distribuidos.inventory.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.distribuidos.inventory.exception.StockUnavailableException;
import com.distribuidos.inventory.model.InventoryProduct;
import com.distribuidos.inventory.model.MovementType;
import com.distribuidos.inventory.model.StockMovement;
import com.distribuidos.inventory.repository.InventoryProductRepository;
import com.distribuidos.inventory.repository.StockMovementRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

/**
 * Verifica el log estructurado de negocio de la reserva: una línea por resultado, con el formato
 * {@code stock.reserve code=… qty=… order=… result=OK|INSUFFICIENT remaining=…}.
 */
@ExtendWith(MockitoExtension.class)
class InventoryReservationLoggingTest {

  @Mock private InventoryProductRepository repository;

  @Mock private StockMovementRepository stockMovementRepository;

  private Logger logger;
  private ListAppender<ILoggingEvent> appender;

  @BeforeEach
  void attachAppender() {
    logger = (Logger) LoggerFactory.getLogger(InventoryReservationService.class);
    appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
  }

  @AfterEach
  void detachAppender() {
    logger.detachAppender(appender);
    appender.stop();
  }

  @Test
  void successfulReserveLogsOkWithRemainingStock() {
    when(repository.findByProductCodeForUpdate("PROD-001")).thenReturn(Optional.of(product(50)));
    when(stockMovementRepository.insertIfAbsent("PROD-001", "RESERVATION", 5, "ORD-001"))
        .thenReturn(1);
    when(repository.reserveAtomically("PROD-001", 5)).thenReturn(1);
    when(repository.findByProductCode("PROD-001")).thenReturn(Optional.of(product(45)));

    service().reserveStock("PROD-001", 5, "ORD-001");

    assertThat(appender.list)
        .anySatisfy(
            event -> {
              assertThat(event.getLevel()).isEqualTo(Level.INFO);
              assertThat(event.getFormattedMessage())
                  .isEqualTo(
                      "stock.reserve code=PROD-001 qty=5 order=ORD-001 result=OK remaining=45");
              assertThat(event.getThrowableProxy()).isNull();
            });
  }

  @Test
  void rejectedReserveLogsInsufficientWithAvailableStock() {
    when(repository.findByProductCodeForUpdate("PROD-001")).thenReturn(Optional.of(product(5)));
    when(stockMovementRepository.insertIfAbsent("PROD-001", "RESERVATION", 5, "ORD-001"))
        .thenReturn(1);
    when(repository.reserveAtomically("PROD-001", 5)).thenReturn(0);
    when(repository.findByProductCode("PROD-001")).thenReturn(Optional.of(product(2)));

    assertThatThrownBy(() -> service().reserveStock("PROD-001", 5, "ORD-001"))
        .isInstanceOf(StockUnavailableException.class);

    assertThat(appender.list)
        .anySatisfy(
            event -> {
              assertThat(event.getLevel()).isEqualTo(Level.INFO);
              assertThat(event.getFormattedMessage())
                  .isEqualTo(
                      "stock.reserve code=PROD-001 qty=5 order=ORD-001 result=INSUFFICIENT remaining=2");
              assertThat(event.getThrowableProxy()).isNull();
            });
  }

  @Test
  void idempotentReplayLogsOkWithoutDecrementingAgain() {
    when(repository.findByProductCodeForUpdate("PROD-001")).thenReturn(Optional.of(product(45)));
    when(stockMovementRepository.findByReferenceOrderNumberAndTypeAndProductCode(
            "ORD-001", MovementType.RESERVATION, "PROD-001"))
        .thenReturn(Optional.of(movement()));
    when(repository.findByProductCode("PROD-001")).thenReturn(Optional.of(product(45)));

    service().reserveStock("PROD-001", 5, "ORD-001");

    assertThat(appender.list)
        .anySatisfy(
            event ->
                assertThat(event.getFormattedMessage())
                    .isEqualTo(
                        "stock.reserve code=PROD-001 qty=5 order=ORD-001 result=OK remaining=45"));
  }

  @Test
  void reserveWithoutOrderNumberLogsNullOrderWithoutStackTrace() {
    when(stockMovementRepository.insertIfAbsent("PROD-001", "RESERVATION", 1, null)).thenReturn(1);
    when(repository.reserveAtomically("PROD-001", 1)).thenReturn(1);
    when(repository.findByProductCode("PROD-001")).thenReturn(Optional.of(product(49)));

    service().reserveStock("PROD-001", 1, null);

    assertThat(appender.list)
        .anySatisfy(
            event -> {
              assertThat(event.getFormattedMessage())
                  .isEqualTo("stock.reserve code=PROD-001 qty=1 order=null result=OK remaining=49");
              assertThat(event.getThrowableProxy()).isNull();
            });
  }

  private InventoryReservationService service() {
    return new InventoryReservationService(repository, stockMovementRepository);
  }

  private static StockMovement movement() {
    StockMovement movement = new StockMovement();
    movement.setProductCode("PROD-001");
    movement.setType(MovementType.RESERVATION);
    movement.setQuantity(5);
    movement.setReferenceOrderNumber("ORD-001");
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
