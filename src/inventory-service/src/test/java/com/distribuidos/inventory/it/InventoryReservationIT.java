package com.distribuidos.inventory.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.distribuidos.inventory.model.MovementType;
import com.distribuidos.inventory.repository.StockMovementRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Pruebas de integración con PostgreSQL real.
 *
 * <p>Con Docker disponible, {@code mvn verify -pl inventory-service} ejecuta pruebas unitarias y de
 * integración. Sin Docker, {@code mvn verify -pl inventory-service} falla con un mensaje explícito;
 * para el ciclo rápido usa {@code mvn test -pl inventory-service}, que ejecuta solo unitarias y
 * deja los ITs sin verificar.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class InventoryReservationIT {

  private static final String POSTGRES_REQUIREMENT_MESSAGE =
      "Los tests de integración de inventory-service requieren un PostgreSQL real "
          + "(Docker en ejecución o un Postgres accesible). "
          + "Para el ciclo rápido sin Docker usa: mvn test -pl inventory-service";

  static {
    requirePostgres();
  }

  @Container
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("postgres:15-alpine")
          .withDatabaseName("inventory_db")
          .withUsername("inventory")
          .withPassword("inventory")
          .withInitScript("inventory-schema.sql");

  private static void requirePostgres() {
    if (!DockerClientFactory.instance().isDockerAvailable()) {
      throw new IllegalStateException(POSTGRES_REQUIREMENT_MESSAGE);
    }
  }

  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    registry.add("spring.sql.init.mode", () -> "never");
    registry.add("eureka.client.enabled", () -> "false");
    registry.add("spring.cloud.discovery.enabled", () -> "false");
  }

  @LocalServerPort private int port;

  @Autowired private TestRestTemplate rest;

  @Autowired private JdbcTemplate jdbc;

  @Autowired private StockMovementRepository stockMovementRepository;

  @Autowired private PlatformTransactionManager transactionManager;

  @Autowired private Environment environment;

  @BeforeEach
  void resetStock() {
    jdbc.update("DELETE FROM stock_movement_history");
    jdbc.update(
        "UPDATE inventory_products SET stock = CASE product_code "
            + "WHEN 'PROD-A' THEN 50 ELSE 30 END, reserved_stock = 0, version = 0 "
            + "WHERE product_code IN ('PROD-A', 'PROD-B')");
  }

  @Test
  void sameOrderReservesEachProductOnceAndIsIdempotentPerProduct() {
    ResponseEntity<String> first = reserve("ORD-001", "PROD-A", 5);
    assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(first.getBody()).contains("\"available\":true");
    assertStock("PROD-A", 45, 5);

    ResponseEntity<String> repeated = reserve("ORD-001", "PROD-A", 5);
    assertThat(repeated.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertStock("PROD-A", 45, 5);

    ResponseEntity<String> secondLine = reserve("ORD-001", "PROD-B", 3);
    assertThat(secondLine.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertStock("PROD-B", 27, 3);

    assertThat(movements("ORD-001", "RESERVATION", "PROD-A")).isEqualTo(1);
    assertThat(movements("ORD-001", "RESERVATION", "PROD-B")).isEqualTo(1);
  }

  @Test
  void twentyConcurrentReservesForSameOrderProductReturnOkAndDecrementOnce() throws Exception {
    ExecutorService pool = Executors.newFixedThreadPool(20);
    CountDownLatch start = new CountDownLatch(1);
    List<Future<ResponseEntity<String>>> futures = new ArrayList<>();
    try {
      for (int i = 0; i < 20; i++) {
        futures.add(
            pool.submit(
                () -> {
                  start.await();
                  return reserve("ORD-CONC", "PROD-A", 1);
                }));
      }
      start.countDown();

      for (Future<ResponseEntity<String>> future : futures) {
        assertThat(future.get(30, TimeUnit.SECONDS).getStatusCode()).isEqualTo(HttpStatus.OK);
      }
    } finally {
      pool.shutdownNow();
      assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
    }

    assertStock("PROD-A", 49, 1);
    assertThat(movements("ORD-CONC", "RESERVATION", "PROD-A")).isEqualTo(1);
  }

  @Test
  void releaseWithoutEnoughReservedStockReturnsConflictAndKeepsInvariant() {
    assertThat(reserve("ORD-REL", "PROD-A", 5).getStatusCode()).isEqualTo(HttpStatus.OK);
    assertStock("PROD-A", 45, 5);

    jdbc.update("UPDATE inventory_products SET reserved_stock = 2 WHERE product_code = 'PROD-A'");

    ResponseEntity<String> response =
        post("/api/inventory/release", Map.of("orderNumber", "ORD-REL"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getBody()).contains("No hay stock reservado suficiente");
    assertStock("PROD-A", 45, 2);
    assertThat(movements("ORD-REL", "RELEASE", "PROD-A")).isZero();
  }

  @Test
  void duplicateConflictInsertReturnsZeroWithoutRollbackOnlyAndRepeatReserveStaysIdempotent() {
    assertThat(reserve("ORD-CONFLICT", "PROD-A", 5).getStatusCode()).isEqualTo(HttpStatus.OK);
    assertStock("PROD-A", 45, 5);

    AtomicBoolean rollbackOnly = new AtomicBoolean();
    Integer inserted =
        new TransactionTemplate(transactionManager)
            .execute(
                status -> {
                  int result =
                      stockMovementRepository.insertIfAbsent(
                          "PROD-A", MovementType.RESERVATION.name(), 5, "ORD-CONFLICT");
                  rollbackOnly.set(status.isRollbackOnly());
                  return result;
                });

    assertThat(inserted).isZero();
    assertThat(rollbackOnly.get()).isFalse();

    ResponseEntity<String> repeated = reserve("ORD-CONFLICT", "PROD-A", 5);
    assertThat(repeated.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertStock("PROD-A", 45, 5);
    assertThat(movements("ORD-CONFLICT", "RESERVATION", "PROD-A")).isEqualTo(1);
  }

  @Test
  void schemaHasReservedStockCheckAndProductAwareUniqueIndex() {
    Integer checks =
        jdbc.queryForObject(
            "SELECT count(*) FROM information_schema.check_constraints "
                + "WHERE check_clause LIKE '%reserved_stock%'",
            Integer.class);
    assertThat(checks).isPositive();

    String indexDefinition =
        jdbc.queryForObject(
            "SELECT indexdef FROM pg_indexes "
                + "WHERE indexname = 'uq_stock_movement_order_product_type'",
            String.class);
    assertThat(indexDefinition).contains("product_code");
  }

  @Test
  void releaseIsIdempotentAndDoesNotRestoreStockTwice() {
    assertThat(reserve("ORD-REL-IDEM", "PROD-A", 5).getStatusCode()).isEqualTo(HttpStatus.OK);
    assertStock("PROD-A", 45, 5);

    ResponseEntity<String> firstRelease =
        post("/api/inventory/release", Map.of("orderNumber", "ORD-REL-IDEM"));
    assertThat(firstRelease.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertStock("PROD-A", 50, 0);
    assertThat(movements("ORD-REL-IDEM", "RELEASE", "PROD-A")).isEqualTo(1);

    ResponseEntity<String> repeatedRelease =
        post("/api/inventory/release", Map.of("orderNumber", "ORD-REL-IDEM"));
    assertThat(repeatedRelease.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(repeatedRelease.getBody()).contains("\"message\":\"Stock liberado exitosamente\"");
    assertStock("PROD-A", 50, 0);
    assertThat(movements("ORD-REL-IDEM", "RELEASE", "PROD-A")).isEqualTo(1);
  }

  @Test
  void fullReserveReleaseFlowLeavesReservationAndReleaseMovements() {
    assertThat(reserve("ORD-FLOW", "PROD-A", 5).getStatusCode()).isEqualTo(HttpStatus.OK);
    assertStock("PROD-A", 45, 5);
    assertThat(movements("ORD-FLOW", "RESERVATION", "PROD-A")).isEqualTo(1);

    ResponseEntity<String> release =
        post("/api/inventory/release", Map.of("orderNumber", "ORD-FLOW"));
    assertThat(release.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertStock("PROD-A", 50, 0);
    assertThat(movements("ORD-FLOW", "RESERVATION", "PROD-A")).isEqualTo(1);
    assertThat(movements("ORD-FLOW", "RELEASE", "PROD-A")).isEqualTo(1);
  }

  @Test
  void reserveUnknownProductReturns404ProblemDetail() {
    ResponseEntity<String> response = reserve("ORD-MISSING", "PROD-999", 1);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getHeaders().getContentType())
        .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    assertThat(response.getBody()).contains("Producto no encontrado: PROD-999");
    assertThat(movements("ORD-MISSING", "RESERVATION", "PROD-999")).isZero();
  }

  @Test
  void reserveWithoutEnoughStockReturns400WithRemainingStockAndNoMovement() {
    jdbc.update("UPDATE inventory_products SET stock = 2 WHERE product_code = 'PROD-B'");

    ResponseEntity<String> response = reserve("ORD-LOW", "PROD-B", 5);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).contains("\"available\":false");
    assertThat(response.getBody()).contains("\"remainingStock\":2");
    assertThat(response.getBody()).contains("Stock insuficiente. Disponible: 2");
    assertStock("PROD-B", 2, 0);
    assertThat(movements("ORD-LOW", "RESERVATION", "PROD-B")).isZero();
  }

  @Test
  void twentyConcurrentReservesOfDistinctOrdersDrainStockToZero() throws Exception {
    jdbc.update("UPDATE inventory_products SET stock = 20 WHERE product_code = 'PROD-A'");

    ExecutorService pool = Executors.newFixedThreadPool(20);
    CountDownLatch start = new CountDownLatch(1);
    List<Future<ResponseEntity<String>>> futures = new ArrayList<>();
    try {
      for (int i = 0; i < 20; i++) {
        String orderNumber = String.format("ORD-DRAIN-%02d", i);
        futures.add(
            pool.submit(
                () -> {
                  start.await();
                  return reserve(orderNumber, "PROD-A", 1);
                }));
      }
      start.countDown();

      for (Future<ResponseEntity<String>> future : futures) {
        assertThat(future.get(30, TimeUnit.SECONDS).getStatusCode()).isEqualTo(HttpStatus.OK);
      }
    } finally {
      pool.shutdownNow();
      assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
    }

    assertStock("PROD-A", 0, 20);
    assertThat(movementsOfType("RESERVATION", "PROD-A")).isEqualTo(20);
  }

  @Test
  void twentyConcurrentReservesWithScarceStockYieldFiveSuccessesAndFifteenRejections()
      throws Exception {
    jdbc.update("UPDATE inventory_products SET stock = 5 WHERE product_code = 'PROD-A'");

    ExecutorService pool = Executors.newFixedThreadPool(20);
    CountDownLatch start = new CountDownLatch(1);
    List<Future<ResponseEntity<String>>> futures = new ArrayList<>();
    try {
      for (int i = 0; i < 20; i++) {
        String orderNumber = String.format("ORD-SCARCE-%02d", i);
        futures.add(
            pool.submit(
                () -> {
                  start.await();
                  return reserve(orderNumber, "PROD-A", 1);
                }));
      }
      start.countDown();

      List<HttpStatusCode> statuses = new ArrayList<>();
      for (Future<ResponseEntity<String>> future : futures) {
        statuses.add(future.get(30, TimeUnit.SECONDS).getStatusCode());
      }

      assertThat(statuses).filteredOn(status -> status == HttpStatus.OK).hasSize(5);
      assertThat(statuses).filteredOn(status -> status == HttpStatus.BAD_REQUEST).hasSize(15);
      assertThat(statuses).hasSize(20);
    } finally {
      pool.shutdownNow();
      assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
    }

    assertStock("PROD-A", 0, 5);
    // Los 15 rechazos no dejan movimiento: la transacción revierte al lanzar
    // StockUnavailableException.
    assertThat(movementsOfType("RESERVATION", "PROD-A")).isEqualTo(5);
  }

  @Test
  void schemaHasIndexOnStockMovementProductCode() {
    String indexDefinition =
        jdbc.queryForObject(
            "SELECT indexdef FROM pg_indexes WHERE indexname = 'idx_stock_movement_product_code'",
            String.class);

    assertThat(indexDefinition).isNotNull().contains("product_code").doesNotContain("UNIQUE");
  }

  @Test
  void jpaOpenInViewIsDisabled() {
    assertThat(environment.getProperty("spring.jpa.open-in-view")).isEqualTo("false");
  }

  @Test
  void getProductSerializesEntityWithoutOpenSessionInView() {
    ResponseEntity<String> response =
        rest.getForEntity("http://localhost:" + port + "/api/inventory/PROD-A", String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody())
        .contains("\"productCode\":\"PROD-A\"")
        .contains("\"stock\":50")
        .contains("\"reservedStock\":0");
    assertThat(response.getHeaders().getFirst("X-Instance-Id")).isNotBlank();
  }

  private ResponseEntity<String> reserve(String orderNumber, String productCode, int quantity) {
    return post(
        "/api/inventory/reserve",
        Map.of("orderNumber", orderNumber, "productCode", productCode, "quantity", quantity));
  }

  private ResponseEntity<String> post(String path, Object body) {
    return rest.postForEntity("http://localhost:" + port + path, body, String.class);
  }

  private void assertStock(String productCode, int expectedStock, int expectedReserved) {
    Integer stock =
        jdbc.queryForObject(
            "SELECT stock FROM inventory_products WHERE product_code = ?",
            Integer.class,
            productCode);
    Integer reserved =
        jdbc.queryForObject(
            "SELECT reserved_stock FROM inventory_products WHERE product_code = ?",
            Integer.class,
            productCode);
    assertThat(stock).isEqualTo(expectedStock);
    assertThat(reserved).isEqualTo(expectedReserved);
  }

  private int movements(String orderNumber, String movementType, String productCode) {
    Integer count =
        jdbc.queryForObject(
            "SELECT count(*) FROM stock_movement_history "
                + "WHERE reference_order_number = ? AND movement_type = ? AND product_code = ?",
            Integer.class,
            orderNumber,
            movementType,
            productCode);
    return count == null ? 0 : count;
  }

  private int movementsOfType(String movementType, String productCode) {
    Integer count =
        jdbc.queryForObject(
            "SELECT count(*) FROM stock_movement_history "
                + "WHERE movement_type = ? AND product_code = ?",
            Integer.class,
            movementType,
            productCode);
    return count == null ? 0 : count;
  }
}
