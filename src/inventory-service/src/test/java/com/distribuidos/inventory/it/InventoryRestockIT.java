package com.distribuidos.inventory.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;

import com.distribuidos.inventory.repository.StockMovementRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Pruebas de integración de la reposición de stock contra PostgreSQL real.
 *
 * <p>Cubre el par UPDATE + INSERT de movimiento como una sola unidad transaccional, que es el
 * requisito explícito del ODD B6-02 ("En la misma transacción"). Las pruebas unitarias mockean el
 * repositorio, así que sin esta clase el rollback del stock cuando falla el insert del movimiento
 * no estaría protegido por ninguna prueba del repositorio.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class InventoryRestockIT {

  private static final String POSTGRES_REQUIREMENT_MESSAGE =
      "Los tests de integración de inventory-service requieren un PostgreSQL real "
          + "(Docker en ejecución o un Postgres accesible). "
          + "Para el ciclo rápido sin Docker usa: mvn test -pl inventory-service";

  private static final String PRODUCT_CODE = "PROD-A";
  private static final int INITIAL_STOCK = 50;

  static {
    if (!DockerClientFactory.instance().isDockerAvailable()) {
      throw new IllegalStateException(POSTGRES_REQUIREMENT_MESSAGE);
    }
  }

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

  @Autowired private ObjectMapper objectMapper;

  /** Espía para forzar el fallo del insert del movimiento y comprobar el rollback del stock. */
  @MockitoSpyBean private StockMovementRepository stockMovementRepository;

  @BeforeEach
  void resetStock() {
    jdbc.update("DELETE FROM stock_movement_history");
    jdbc.update(
        "UPDATE inventory_products SET stock = ?, reserved_stock = 0, version = 0 "
            + "WHERE product_code = ?",
        INITIAL_STOCK,
        PRODUCT_CODE);
  }

  @Test
  void restockIncreasesStockAndRecordsRestockMovement() throws Exception {
    OffsetDateTime updatedBefore = updatedAt(PRODUCT_CODE);

    ResponseEntity<String> response = restock(PRODUCT_CODE, 25);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode body = objectMapper.readTree(response.getBody());
    assertThat(body.get("productCode").asText()).isEqualTo(PRODUCT_CODE);
    assertThat(body.get("quantity").asInt()).isEqualTo(25);
    assertThat(body.get("stock").asInt()).isEqualTo(INITIAL_STOCK + 25);
    assertThat(body.get("message").asText()).isEqualTo("Stock reabastecido exitosamente");

    assertThat(stock(PRODUCT_CODE)).isEqualTo(INITIAL_STOCK + 25);
    assertThat(reservedStock(PRODUCT_CODE)).isZero();
    assertThat(version(PRODUCT_CODE)).isEqualTo(1L);
    assertThat(updatedAt(PRODUCT_CODE)).isAfter(updatedBefore);

    List<String> movements =
        jdbc.queryForList(
            "select movement_type from stock_movement_history where product_code = ?",
            String.class,
            PRODUCT_CODE);
    assertThat(movements).containsExactly("RESTOCK");
    assertThat(
            jdbc.queryForObject(
                "select quantity from stock_movement_history where product_code = ?",
                Integer.class,
                PRODUCT_CODE))
        .isEqualTo(25);
    assertThat(
            jdbc.queryForObject(
                "select reference_order_number from stock_movement_history where product_code = ?",
                String.class,
                PRODUCT_CODE))
        .isNull();
  }

  @Test
  void consecutiveRestocksAreNotDeduplicated() {
    assertThat(restock(PRODUCT_CODE, 25).getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(restock(PRODUCT_CODE, 10).getStatusCode()).isEqualTo(HttpStatus.OK);

    assertThat(stock(PRODUCT_CODE)).isEqualTo(INITIAL_STOCK + 35);
    assertThat(jdbc.queryForObject("select count(*) from stock_movement_history", Integer.class))
        .isEqualTo(2);
  }

  @Test
  void restockUnknownProductReturns404AndRecordsNoMovement() throws Exception {
    ResponseEntity<String> response = restock("PROD-ZZZ", 5);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    JsonNode body = objectMapper.readTree(response.getBody());
    assertThat(body.get("type").asText()).isEqualTo("about:blank");
    assertThat(body.get("title").asText()).isEqualTo("Not Found");
    assertThat(body.get("status").asInt()).isEqualTo(404);
    assertThat(body.get("detail").asText()).isEqualTo("Producto PROD-ZZZ no encontrado");
    assertThat(body.get("instance").asText()).isEqualTo("/api/inventory/PROD-ZZZ/stock");

    assertThat(jdbc.queryForObject("select count(*) from stock_movement_history", Integer.class))
        .isZero();
    assertThat(stock(PRODUCT_CODE)).isEqualTo(INITIAL_STOCK);
  }

  @Test
  void restockRollsBackStockWhenMovementInsertFails() {
    doThrow(new IllegalStateException("movimiento no insertable"))
        .when(stockMovementRepository)
        .insertIfAbsent(anyString(), anyString(), anyInt(), any());

    ResponseEntity<String> response = restock(PRODUCT_CODE, 25);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(stock(PRODUCT_CODE)).isEqualTo(INITIAL_STOCK);
    assertThat(version(PRODUCT_CODE)).isZero();
    assertThat(jdbc.queryForObject("select count(*) from stock_movement_history", Integer.class))
        .isZero();
  }

  private ResponseEntity<String> restock(String productCode, int quantity) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    return rest.exchange(
        "/api/inventory/" + productCode + "/stock",
        HttpMethod.PUT,
        new HttpEntity<>("{\"quantity\":" + quantity + "}", headers),
        String.class);
  }

  private int stock(String productCode) {
    return jdbc.queryForObject(
        "select stock from inventory_products where product_code = ?", Integer.class, productCode);
  }

  private int reservedStock(String productCode) {
    return jdbc.queryForObject(
        "select reserved_stock from inventory_products where product_code = ?",
        Integer.class,
        productCode);
  }

  private long version(String productCode) {
    return jdbc.queryForObject(
        "select version from inventory_products where product_code = ?", Long.class, productCode);
  }

  private OffsetDateTime updatedAt(String productCode) {
    return jdbc.queryForObject(
        "select updated_at from inventory_products where product_code = ?",
        OffsetDateTime.class,
        productCode);
  }
}
