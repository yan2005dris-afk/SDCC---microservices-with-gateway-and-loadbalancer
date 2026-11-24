package com.distribuidos.order.service;

import com.distribuidos.order.datasource.DbNode;
import com.distribuidos.order.datasource.RoutingContextHolder;
import com.distribuidos.order.dto.CreateOrderRequest;
import com.distribuidos.order.dto.OrderResponse;
import com.distribuidos.order.loadbalancer.InventoryStockClient;
import com.distribuidos.order.loadbalancer.dto.InventoryReserveRequest;
import com.distribuidos.order.loadbalancer.dto.InventoryReserveResponse;
import com.distribuidos.order.model.Order;
import com.distribuidos.order.model.OrderItem;
import com.distribuidos.order.observability.MdcKeys;
import com.distribuidos.order.repository.OrderRepository;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClientException;

/**
 * Caso de uso de creación de pedidos (P04, P05).
 *
 * <p>La llamada a {@code inventory-service} se hace <b>fuera</b> de cualquier transacción: no se
 * mantiene una conexión de base de datos abierta durante una llamada HTTP. La escritura usa una
 * transacción corta en el primario.
 */
@Service
public class OrderCommandService {

  private static final Logger log = LoggerFactory.getLogger(OrderCommandService.class);

  private static final String CONFIRMED_STATUS = "CONFIRMED";
  private static final String ORDER_NUMBER_PREFIX = "ORD-";
  private static final int ORDER_NUMBER_SUFFIX_LENGTH = 8;

  private final OrderRepository orderRepository;
  private final InventoryStockClient inventoryStockClient;
  private final TransactionTemplate writeTransaction;

  /**
   * Crea el servicio.
   *
   * @param orderRepository repositorio de pedidos.
   * @param inventoryStockClient cliente balanceado de inventario.
   * @param transactionManager gestor de transacciones JPA.
   */
  public OrderCommandService(
      OrderRepository orderRepository,
      InventoryStockClient inventoryStockClient,
      PlatformTransactionManager transactionManager) {
    this.orderRepository = orderRepository;
    this.inventoryStockClient = inventoryStockClient;
    this.writeTransaction = new TransactionTemplate(transactionManager);
  }

  /**
   * Reserva stock en inventario y, si hay disponibilidad, persiste el pedido en el primario.
   *
   * @param request datos del pedido.
   * @return pedido creado.
   * @throws OrderRejectedException si faltan datos, no hay stock o inventario no responde.
   */
  public OrderResponse createOrder(CreateOrderRequest request) {
    if (request == null || !request.hasRequiredFields()) {
      throw new OrderRejectedException("Datos incompletos para crear orden");
    }
    reserveStock(request);
    return persist(buildOrder(request));
  }

  private void reserveStock(CreateOrderRequest request) {
    InventoryReserveResponse response;
    try {
      response =
          inventoryStockClient.reserveStock(
              new InventoryReserveRequest(request.productCode(), request.quantity()));
    } catch (RestClientException ex) {
      log.error("Fallo comunicación inter-servicio con inventory-service", ex);
      throw new OrderRejectedException("Error al verificar inventario", ex);
    }
    if (!response.isAvailable()) {
      throw new OrderRejectedException("Stock insuficiente");
    }
  }

  private OrderResponse persist(Order order) {
    long start = System.nanoTime();
    RoutingContextHolder.set(DbNode.PRIMARY);
    MDC.put(MdcKeys.DB_NODE, DbNode.PRIMARY.name());
    try {
      OrderResponse saved =
          writeTransaction.execute(status -> OrderResponse.from(orderRepository.save(order)));
      log.info(
          "db.route op=WRITE node={} result=OK durationMs={}",
          DbNode.PRIMARY,
          TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
      return saved;
    } finally {
      RoutingContextHolder.clear();
      MDC.remove(MdcKeys.DB_NODE);
    }
  }

  private static Order buildOrder(CreateOrderRequest request) {
    BigDecimal subtotal = request.unitPrice().multiply(BigDecimal.valueOf(request.quantity()));
    Order order = new Order();
    order.setOrderNumber(
        ORDER_NUMBER_PREFIX
            + UUID.randomUUID().toString().substring(0, ORDER_NUMBER_SUFFIX_LENGTH).toUpperCase());
    order.setCustomerName(request.resolvedCustomerName());
    order.setCustomerEmail(request.resolvedCustomerEmail());
    order.setStatus(CONFIRMED_STATUS);
    order.setTotalAmount(subtotal);

    OrderItem item = new OrderItem();
    item.setOrder(order);
    item.setProductCode(request.productCode());
    item.setProductName("Producto " + request.productCode());
    item.setQuantity(request.quantity());
    item.setUnitPrice(request.unitPrice());
    item.setSubtotal(subtotal);
    order.getItems().add(item);
    return order;
  }
}
