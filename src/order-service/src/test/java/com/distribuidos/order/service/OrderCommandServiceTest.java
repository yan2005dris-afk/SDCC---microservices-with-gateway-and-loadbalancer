package com.distribuidos.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.distribuidos.order.datasource.DbNode;
import com.distribuidos.order.datasource.RoutingContextHolder;
import com.distribuidos.order.dto.CreateOrderRequest;
import com.distribuidos.order.loadbalancer.InventoryStockClient;
import com.distribuidos.order.loadbalancer.InventoryUnavailableException;
import com.distribuidos.order.loadbalancer.ProductNotFoundException;
import com.distribuidos.order.loadbalancer.ReservedStock;
import com.distribuidos.order.loadbalancer.StockUnavailableException;
import com.distribuidos.order.model.Order;
import com.distribuidos.order.repository.OrderRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

class OrderCommandServiceTest {

  private static final ReservedStock LAPTOP =
      new ReservedStock("PROD-001", "Laptop Dell XPS 15", new BigDecimal("1899.99"));

  private OrderRepository orderRepository;
  private InventoryStockClient inventory;
  private OrderCommandService service;

  @BeforeEach
  void setUp() {
    orderRepository = mock(OrderRepository.class);
    inventory = mock(InventoryStockClient.class);
    PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    service =
        new OrderCommandService(
            orderRepository, inventory, transactionManager, Duration.ofSeconds(1));
    when(orderRepository.findByOrderNumber(anyString())).thenReturn(Optional.empty());
  }

  private static CreateOrderRequest request(int quantity) {
    return new CreateOrderRequest("Juan Perez", "juan@test.com", "PROD-001", quantity);
  }

  @Test
  void priceAndNameComeFromInventoryAndOrderIsWrittenOnPrimary() {
    when(inventory.reserve(eq("PROD-001"), eq(2), anyString())).thenReturn(LAPTOP);
    AtomicReference<DbNode> nodeDuringSave = new AtomicReference<>();
    when(orderRepository.save(any(Order.class)))
        .thenAnswer(
            invocation -> {
              nodeDuringSave.set(RoutingContextHolder.get());
              return invocation.getArgument(0);
            });

    OrderCreation result = service.createOrder(request(2), null);

    assertThat(result.replayed()).isFalse();
    assertThat(result.order().totalAmount()).isEqualByComparingTo("3799.98");
    assertThat(result.order().items().get(0).productName()).isEqualTo("Laptop Dell XPS 15");
    assertThat(result.order().items().get(0).unitPrice()).isEqualByComparingTo("1899.99");
    assertThat(nodeDuringSave.get()).isEqualTo(DbNode.PRIMARY);
    assertThat(RoutingContextHolder.get()).isNull();
  }

  @Test
  void orderNumberSentToInventoryIsTheOnePersisted() {
    ArgumentCaptor<String> sent = ArgumentCaptor.forClass(String.class);
    when(inventory.reserve(anyString(), anyInt(), sent.capture())).thenReturn(LAPTOP);
    when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

    OrderCreation result = service.createOrder(request(1), null);

    assertThat(sent.getValue()).startsWith("ORD-").isEqualTo(result.order().orderNumber());
  }

  @Test
  void insufficientStockDoesNotPersistNorCompensate() {
    when(inventory.reserve(anyString(), anyInt(), anyString()))
        .thenThrow(new StockUnavailableException("PROD-001", 2));

    assertThatThrownBy(() -> service.createOrder(request(999), null))
        .isInstanceOf(StockUnavailableException.class);
    verify(orderRepository, never()).save(any());
    verify(inventory, never()).release(anyString());
  }

  @Test
  void unknownProductDoesNotPersist() {
    when(inventory.reserve(anyString(), anyInt(), anyString()))
        .thenThrow(new ProductNotFoundException("PROD-X"));

    assertThatThrownBy(() -> service.createOrder(request(1), null))
        .isInstanceOf(ProductNotFoundException.class);
    verify(orderRepository, never()).save(any());
  }

  @Test
  void inventoryTimeoutCompensatesBecauseTheReservationMayExist() {
    when(inventory.reserve(anyString(), anyInt(), anyString()))
        .thenThrow(new InventoryUnavailableException("timeout", true, null));

    assertThatThrownBy(() -> service.createOrder(request(1), null))
        .isInstanceOf(InventoryUnavailableException.class);
    verify(orderRepository, never()).save(any());
    verify(inventory).release(anyString());
  }

  @Test
  void inventoryUnreachableDoesNotCompensate() {
    when(inventory.reserve(anyString(), anyInt(), anyString()))
        .thenThrow(new InventoryUnavailableException("refused", false, null));

    assertThatThrownBy(() -> service.createOrder(request(1), null))
        .isInstanceOf(InventoryUnavailableException.class);
    verify(inventory, never()).release(anyString());
  }

  @Test
  void persistenceFailureReleasesTheReservation() {
    ArgumentCaptor<String> reserved = ArgumentCaptor.forClass(String.class);
    when(inventory.reserve(anyString(), anyInt(), reserved.capture())).thenReturn(LAPTOP);
    when(orderRepository.save(any(Order.class))).thenThrow(new QueryTimeoutException("timeout"));

    assertThatThrownBy(() -> service.createOrder(request(1), null))
        .isInstanceOf(OrderPersistenceException.class);
    verify(inventory).release(reserved.getValue());
  }

  @Test
  void failedCompensationStillReportsThePersistenceError() {
    when(inventory.reserve(anyString(), anyInt(), anyString())).thenReturn(LAPTOP);
    when(orderRepository.save(any(Order.class))).thenThrow(new QueryTimeoutException("timeout"));
    doThrow(new InventoryUnavailableException("down", true, null))
        .when(inventory)
        .release(anyString());

    assertThatThrownBy(() -> service.createOrder(request(1), null))
        .isInstanceOf(OrderPersistenceException.class);
  }

  @Test
  void sameIdempotencyKeyReturnsTheExistingOrderWithoutReservingAgain() {
    when(inventory.reserve(anyString(), anyInt(), anyString())).thenReturn(LAPTOP);
    AtomicReference<Order> stored = new AtomicReference<>();
    when(orderRepository.save(any(Order.class)))
        .thenAnswer(
            i -> {
              stored.set(i.getArgument(0));
              return i.getArgument(0);
            });

    OrderCreation first = service.createOrder(request(1), "client-key-1");
    when(orderRepository.findByOrderNumber(first.order().orderNumber()))
        .thenReturn(Optional.of(stored.get()));
    OrderCreation retry = service.createOrder(request(1), "client-key-1");

    assertThat(first.replayed()).isFalse();
    assertThat(retry.replayed()).isTrue();
    assertThat(retry.order().orderNumber()).isEqualTo(first.order().orderNumber());
    verify(inventory, times(1)).reserve(anyString(), anyInt(), anyString());
  }

  @Test
  void concurrentDuplicateKeyReturnsTheWinnerAndDoesNotCompensate() {
    String orderNumber = OrderCommandService.orderNumberFor("client-key-2");
    Order winner = new Order();
    winner.setOrderNumber(orderNumber);
    when(inventory.reserve(anyString(), anyInt(), anyString())).thenReturn(LAPTOP);
    when(orderRepository.save(any(Order.class)))
        .thenThrow(new DataIntegrityViolationException("uk_order_number"));
    when(orderRepository.findByOrderNumber(orderNumber))
        .thenReturn(Optional.empty(), Optional.of(winner));

    OrderCreation result = service.createOrder(request(1), "client-key-2");

    assertThat(result.replayed()).isTrue();
    verify(inventory, never()).release(anyString());
  }

  @Test
  void idempotencyKeyMapsToAStableBoundedOrderNumber() {
    String a = OrderCommandService.orderNumberFor("k");
    assertThat(a).isEqualTo(OrderCommandService.orderNumberFor("k")).hasSize(28);
    assertThat(a).isNotEqualTo(OrderCommandService.orderNumberFor("k2"));
  }

  @Test
  void incompleteRequestIsRejectedWithoutCallingInventory() {
    assertThatThrownBy(() -> service.createOrder(new CreateOrderRequest(null, null, null, 0), null))
        .isInstanceOf(OrderRejectedException.class)
        .hasMessage("Datos incompletos para crear orden");
    verify(inventory, never()).reserve(anyString(), anyInt(), anyString());
  }
}
