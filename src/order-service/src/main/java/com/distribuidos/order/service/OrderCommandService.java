package com.distribuidos.order.service;

import com.distribuidos.order.datasource.DbNode;
import com.distribuidos.order.datasource.RoutingContextHolder;
import com.distribuidos.order.dto.CreateOrderRequest;
import com.distribuidos.order.dto.OrderResponse;
import com.distribuidos.order.loadbalancer.InventoryStockClient;
import com.distribuidos.order.loadbalancer.InventoryUnavailableException;
import com.distribuidos.order.loadbalancer.ReservedStock;
import com.distribuidos.order.model.Order;
import com.distribuidos.order.model.OrderItem;
import com.distribuidos.order.observability.MdcKeys;
import com.distribuidos.order.repository.OrderRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Creación de pedidos (P04, P05, ORD-06, ORD-07).
 *
 * <ol>
 *   <li>Valida la petición y obtiene el {@code orderNumber}: derivado de {@code Idempotency-Key} si
 *       el cliente la envía, o aleatorio.
 *   <li>Con clave, si el pedido ya existe lo devuelve (reintento del cliente).
 *   <li>Reserva en inventario <b>fuera de cualquier transacción</b>: no se mantiene una conexión a
 *       BD abierta durante la llamada HTTP. El precio y el nombre salen de inventario.
 *   <li>Persiste en {@code PRIMARY} en una transacción corta.
 *   <li>Si la reserva pudo aplicarse pero el pedido no se registra (timeout o 5xx de inventario, o
 *       fallo al persistir), libera la reserva <i>best-effort</i>. Si la liberación también falla
 *       queda en el log ({@code result=COMPENSATION_FAILED}) para reconciliarla a mano: la consigna
 *       excluye Sagas.
 * </ol>
 */
@Service
public class OrderCommandService {

  private static final Logger log = LoggerFactory.getLogger(OrderCommandService.class);
  private static final String CONFIRMED_STATUS = "CONFIRMED";
  private static final String ORDER_NUMBER_PREFIX = "ORD-";
  private static final int RANDOM_SUFFIX_LENGTH = 12;
  private static final int KEY_HASH_LENGTH = 24;
  private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 128;

  private final OrderRepository orderRepository;
  private final InventoryStockClient inventoryStockClient;
  private final TransactionTemplate writeTransaction;
  private final TransactionTemplate primaryRead;

  /**
   * Crea el servicio.
   *
   * @param orderRepository repositorio de pedidos.
   * @param inventoryStockClient cliente de inventario.
   * @param transactionManager gestor de transacciones.
   * @param writeTimeout tiempo máximo de la transacción de escritura (presupuesto del Gateway).
   */
  public OrderCommandService(
      OrderRepository orderRepository,
      InventoryStockClient inventoryStockClient,
      PlatformTransactionManager transactionManager,
      @Value("${sdcc.order.write-timeout:1s}") Duration writeTimeout) {
    this.orderRepository = orderRepository;
    this.inventoryStockClient = inventoryStockClient;
    this.writeTransaction = new TransactionTemplate(transactionManager);
    this.writeTransaction.setTimeout((int) Math.max(1, writeTimeout.toSeconds()));
    this.primaryRead = new TransactionTemplate(transactionManager);
    this.primaryRead.setReadOnly(true);
  }

  /**
   * Crea un pedido.
   *
   * @param request datos del pedido.
   * @param idempotencyKey clave de idempotencia del cliente, o {@code null}.
   * @return pedido creado, o el existente si la clave ya se usó.
   * @throws OrderRejectedException si faltan datos.
   * @throws com.distribuidos.order.loadbalancer.StockUnavailableException sin stock.
   * @throws com.distribuidos.order.loadbalancer.ProductNotFoundException producto inexistente.
   * @throws InventoryUnavailableException inventario no disponible.
   * @throws OrderPersistenceException si no se pudo registrar el pedido.
   */
  public OrderCreation createOrder(CreateOrderRequest request, String idempotencyKey) {
    if (request == null || !request.hasRequiredFields()) {
      throw new OrderRejectedException("Datos incompletos para crear orden");
    }
    String key = normalize(idempotencyKey);
    String orderNumber = key == null ? randomOrderNumber() : orderNumberFor(key);

    if (key != null) {
      Optional<OrderResponse> existing = findOnPrimary(orderNumber);
      if (existing.isPresent()) {
        logResult(orderNumber, request, "REPLAYED");
        return OrderCreation.replayed(existing.get());
      }
    }

    ReservedStock stock = reserve(request, orderNumber);
    try {
      OrderResponse saved = persist(buildOrder(request, orderNumber, stock));
      logResult(orderNumber, request, "CREATED");
      return OrderCreation.created(saved);
    } catch (DataIntegrityViolationException ex) {
      // Dos peticiones simultáneas con la misma clave: la otra ya registró el pedido y la reserva
      // (idempotente en inventario) le pertenece. No se compensa.
      Optional<OrderResponse> winner = key == null ? Optional.empty() : findOnPrimary(orderNumber);
      if (winner.isPresent()) {
        logResult(orderNumber, request, "REPLAYED");
        return OrderCreation.replayed(winner.get());
      }
      throw compensateAndFail(orderNumber, request, ex);
    } catch (RuntimeException ex) {
      throw compensateAndFail(orderNumber, request, ex);
    }
  }

  private ReservedStock reserve(CreateOrderRequest request, String orderNumber) {
    try {
      return inventoryStockClient.reserve(request.productCode(), request.quantity(), orderNumber);
    } catch (InventoryUnavailableException ex) {
      if (ex.reservationMayExist()) {
        compensate(orderNumber, request);
      } else {
        logResult(orderNumber, request, "INVENTORY_UNAVAILABLE");
      }
      throw ex;
    } catch (RuntimeException ex) {
      logResult(orderNumber, request, "REJECTED reason=" + ex.getClass().getSimpleName());
      throw ex;
    }
  }

  private OrderPersistenceException compensateAndFail(
      String orderNumber, CreateOrderRequest request, RuntimeException cause) {
    log.error("order.persist orderNumber={} result=FAILED", orderNumber, cause);
    compensate(orderNumber, request);
    return new OrderPersistenceException(orderNumber, cause);
  }

  private void compensate(String orderNumber, CreateOrderRequest request) {
    try {
      inventoryStockClient.release(orderNumber);
      logResult(orderNumber, request, "COMPENSATED");
    } catch (RuntimeException ex) {
      log.error(
          "order.create orderNumber={} product={} qty={} result=COMPENSATION_FAILED"
              + " action=reconciliar-reserva-manualmente",
          orderNumber,
          request.productCode(),
          request.quantity(),
          ex);
    }
  }

  private Optional<OrderResponse> findOnPrimary(String orderNumber) {
    return onPrimary(
        () ->
            primaryRead.execute(
                status -> orderRepository.findByOrderNumber(orderNumber).map(OrderResponse::from)));
  }

  private OrderResponse persist(Order order) {
    long start = System.nanoTime();
    OrderResponse saved =
        onPrimary(
            () ->
                writeTransaction.execute(
                    status -> OrderResponse.from(orderRepository.save(order))));
    log.info(
        "db.route op=WRITE node={} result=OK durationMs={}",
        DbNode.PRIMARY,
        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
    return saved;
  }

  private static <T> T onPrimary(Supplier<T> action) {
    RoutingContextHolder.set(DbNode.PRIMARY);
    MDC.put(MdcKeys.DB_NODE, DbNode.PRIMARY.name());
    try {
      return action.get();
    } finally {
      RoutingContextHolder.clear();
      MDC.remove(MdcKeys.DB_NODE);
    }
  }

  private static void logResult(String orderNumber, CreateOrderRequest request, String result) {
    log.info(
        "order.create orderNumber={} product={} qty={} result={}",
        orderNumber,
        request.productCode(),
        request.quantity(),
        result);
  }

  private static Order buildOrder(
      CreateOrderRequest request, String orderNumber, ReservedStock stock) {
    BigDecimal subtotal = stock.unitPrice().multiply(BigDecimal.valueOf(request.quantity()));
    Order order = new Order();
    order.setOrderNumber(orderNumber);
    order.setCustomerName(request.resolvedCustomerName());
    order.setCustomerEmail(request.resolvedCustomerEmail());
    order.setStatus(CONFIRMED_STATUS);
    order.setTotalAmount(subtotal);

    OrderItem item = new OrderItem();
    item.setOrder(order);
    item.setProductCode(stock.productCode());
    item.setProductName(stock.productName());
    item.setQuantity(request.quantity());
    item.setUnitPrice(stock.unitPrice());
    item.setSubtotal(subtotal);
    order.getItems().add(item);
    return order;
  }

  private static String normalize(String idempotencyKey) {
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      return null;
    }
    String key = idempotencyKey.strip();
    if (key.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
      throw new OrderRejectedException(
          "Idempotency-Key supera " + MAX_IDEMPOTENCY_KEY_LENGTH + " caracteres");
    }
    return key;
  }

  private static String randomOrderNumber() {
    return ORDER_NUMBER_PREFIX
        + UUID.randomUUID().toString().replace("-", "").substring(0, RANDOM_SUFFIX_LENGTH).toUpperCase();
  }

  /**
   * {@code orderNumber} determinista a partir de la clave: el mismo reintento produce el mismo
   * pedido. Se usa un hash para acotar la longitud ({@code order_number} es {@code VARCHAR(64)}) y
   * no guardar la clave tal cual.
   */
  static String orderNumberFor(String idempotencyKey) {
    try {
      byte[] hash =
          MessageDigest.getInstance("SHA-256").digest(idempotencyKey.getBytes(StandardCharsets.UTF_8));
      return ORDER_NUMBER_PREFIX
          + HexFormat.of().formatHex(hash).substring(0, KEY_HASH_LENGTH).toUpperCase();
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 no disponible", ex);
    }
  }
}
