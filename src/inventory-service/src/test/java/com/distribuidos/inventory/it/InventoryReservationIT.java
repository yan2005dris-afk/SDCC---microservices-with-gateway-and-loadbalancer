package com.distribuidos.inventory.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class InventoryReservationIT {

  @Container
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("postgres:15-alpine")
          .withDatabaseName("inventory_db")
          .withUsername("inventory")
          .withPassword("inventory")
          .withInitScript("inventory-schema.sql");

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
}
