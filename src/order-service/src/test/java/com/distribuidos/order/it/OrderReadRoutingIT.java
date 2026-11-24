package com.distribuidos.order.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.distribuidos.order.datasource.ReplicaDataSources;
import com.distribuidos.order.datasource.health.ReplicaProbe;
import com.distribuidos.order.datasource.health.ReplicaStatus;
import com.distribuidos.order.dto.OrderResponse;
import com.distribuidos.order.loadbalancer.InventoryStockClient;
import com.distribuidos.order.loadbalancer.dto.InventoryReserveResponse;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Enrutamiento de lecturas y escrituras contra tres PostgreSQL reales (ORD-01, ORD-04, ORD-08).
 *
 * <p>Los tres contenedores son independientes y cada uno tiene un pedido "marcador" cuyo cliente es
 * el nombre del nodo. Así el test comprueba <b>qué base respondió</b>, no solo la cabecera. No hace
 * falta replicación WAL real: lo que se prueba es el enrutamiento y el failover con transacciones
 * reales, que es lo que los tests con mocks no podían detectar.
 *
 * <p>Como los contenedores no replican, el sondeo de aptitud real los marcaría {@code LAGGING}.
 * Aquí se sustituye por un sondeo de conectividad: este test prueba el enrutamiento, y {@code
 * PostgresReplicaProbeIT} y el smoke test de Compose prueban la aptitud con replicación real.
 */
@Testcontainers
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "eureka.client.enabled=false",
      "spring.jpa.hibernate.ddl-auto=none",
      "sdcc.shutdown.drain-delay=0s",
      "INSTANCE_ID=order-service-it"
    })
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OrderReadRoutingIT {

  private static final String DB_USER = "admin";
  private static final String DB_PASSWORD = "it_only_password";

  /** Por debajo del timelimiter del Gateway (3 s), con margen para entornos de CI lentos. */
  private static final Duration MAX_FAILOVER_LATENCY = Duration.ofMillis(2500);

  @Container static final PostgreSQLContainer<?> PRIMARY = node();
  @Container static final PostgreSQLContainer<?> REPLICA_1 = node();
  @Container static final PostgreSQLContainer<?> REPLICA_2 = node();

  @MockitoBean private InventoryStockClient inventoryStockClient;
  @Autowired private TestRestTemplate rest;

  private static PostgreSQLContainer<?> node() {
    return new PostgreSQLContainer<>("postgres:17")
        .withDatabaseName("orders_db")
        .withUsername(DB_USER)
        .withPassword(DB_PASSWORD)
        .withInitScript("it/orders-schema.sql");
  }

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add("DB_PRIMARY_URL", PRIMARY::getJdbcUrl);
    registry.add("DB_REPLICA1_URL", REPLICA_1::getJdbcUrl);
    registry.add("DB_REPLICA2_URL", REPLICA_2::getJdbcUrl);
    registry.add("DB_USER", () -> DB_USER);
    registry.add("DB_PASSWORD", () -> DB_PASSWORD);
  }

  @BeforeAll
  static void seedMarkers() throws SQLException {
    insertMarker(PRIMARY, "PRIMARY");
    insertMarker(REPLICA_1, "REPLICA_1");
    insertMarker(REPLICA_2, "REPLICA_2");
  }

  @Test
  @Order(1)
  void ordinaryReadsAreServedByBothReplicasFromTheReportedNode() {
    Set<String> nodes = new HashSet<>();
    for (int i = 0; i < 4; i++) {
      ResponseEntity<OrderResponse[]> response =
          rest.getForEntity("/api/orders", OrderResponse[].class);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      String node = response.getHeaders().getFirst("X-Database-Node");
      assertThat(response.getBody()).extracting(OrderResponse::customerName).containsExactly(node);
      nodes.add(node);
    }
    assertThat(nodes).containsExactlyInAnyOrder("REPLICA_1", "REPLICA_2");
  }

  @Test
  @Order(2)
  void criticalReadIsServedByPrimary() {
    ResponseEntity<OrderResponse> response =
        rest.exchange(
            "/api/orders/1",
            HttpMethod.GET,
            new HttpEntity<>(headers("X-Consistency", "critical")),
            OrderResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getHeaders().getFirst("X-Database-Node")).isEqualTo("PRIMARY");
    assertThat(response.getBody().customerName()).isEqualTo("PRIMARY");
  }

  @Test
  @Order(3)
  void writesGoOnlyToPrimary() throws SQLException {
    when(inventoryStockClient.reserveStock(any()))
        .thenReturn(new InventoryReserveResponse("PROD-001", true, 10, null));
    HttpHeaders headers = headers("Content-Type", MediaType.APPLICATION_JSON_VALUE);
    String body = "{\"productCode\":\"PROD-001\",\"quantity\":1,\"unitPrice\":10.00}";

    ResponseEntity<OrderResponse> response =
        rest.postForEntity("/api/orders", new HttpEntity<>(body, headers), OrderResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getHeaders().getFirst("X-Database-Node")).isEqualTo("PRIMARY");
    assertThat(countOrders(PRIMARY)).isEqualTo(2);
    assertThat(countOrders(REPLICA_1)).isEqualTo(1);
    assertThat(countOrders(REPLICA_2)).isEqualTo(1);
  }

  @Test
  @Order(4)
  void readsKeepSucceedingFromHealthyReplicaWhenOneReplicaGoesDown() {
    REPLICA_1.stop();

    for (int i = 0; i < 6; i++) {
      long start = System.nanoTime();
      ResponseEntity<OrderResponse[]> list =
          rest.getForEntity("/api/orders", OrderResponse[].class);
      Duration listLatency = Duration.ofNanos(System.nanoTime() - start);

      // ORD-01: ninguna lectura devuelve 500, tampoco la primera tras la caída.
      assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(list.getHeaders().getFirst("X-Database-Node")).isEqualTo("REPLICA_2");
      assertThat(list.getBody())
          .extracting(OrderResponse::customerName)
          .containsExactly("REPLICA_2");
      assertThat(listLatency).isLessThan(MAX_FAILOVER_LATENCY);

      // ORD-04: la consulta por id también hace failover.
      ResponseEntity<OrderResponse> byId = rest.getForEntity("/api/orders/1", OrderResponse.class);
      assertThat(byId.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(byId.getHeaders().getFirst("X-Database-Node")).isEqualTo("REPLICA_2");
      assertThat(byId.getBody().customerName()).isEqualTo("REPLICA_2");
    }
  }

  private static HttpHeaders headers(String name, String value) {
    HttpHeaders headers = new HttpHeaders();
    headers.set(name, value);
    return headers;
  }

  private static void insertMarker(PostgreSQLContainer<?> node, String nodeName)
      throws SQLException {
    try (Connection connection = open(node);
        Statement statement = connection.createStatement()) {
      statement.executeUpdate(
          "INSERT INTO orders (order_number, customer_name, customer_email, total_amount, status)"
              + " VALUES ('MARK-"
              + nodeName
              + "', '"
              + nodeName
              + "', 'marker@it.local', 0, 'MARKER')");
    }
  }

  private static int countOrders(PostgreSQLContainer<?> node) throws SQLException {
    try (Connection connection = open(node);
        Statement statement = connection.createStatement();
        ResultSet rs = statement.executeQuery("SELECT count(*) FROM orders")) {
      rs.next();
      return rs.getInt(1);
    }
  }

  private static Connection open(PostgreSQLContainer<?> node) throws SQLException {
    return DriverManager.getConnection(node.getJdbcUrl(), DB_USER, DB_PASSWORD);
  }

  /** Sondeo de conectividad: {@code UP} si la réplica acepta conexiones. */
  @TestConfiguration
  static class ConnectivityProbeConfig {

    @Bean
    @Primary
    ReplicaProbe connectivityProbe(@Qualifier("replicaDataSources") ReplicaDataSources pools) {
      return node -> {
        try (Connection connection = pools.asMap().get(node).getConnection()) {
          return connection.isValid(1) ? ReplicaStatus.up(0) : ReplicaStatus.down("invalid");
        } catch (SQLException ex) {
          return ReplicaStatus.down("unreachable");
        }
      };
    }
  }
}
