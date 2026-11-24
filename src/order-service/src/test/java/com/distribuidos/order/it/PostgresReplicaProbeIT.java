package com.distribuidos.order.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.distribuidos.order.datasource.DbNode;
import com.distribuidos.order.datasource.ReplicaDataSources;
import com.distribuidos.order.datasource.health.PostgresReplicaProbe;
import com.distribuidos.order.datasource.health.ReplicaHealthProperties;
import com.distribuidos.order.datasource.health.ReplicaState;
import com.distribuidos.order.datasource.health.ReplicaStatus;
import com.zaxxer.hikari.HikariDataSource;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Consulta de aptitud contra PostgreSQL real. El caso {@code UP} (réplica en streaming) se prueba
 * en el smoke test de Compose, donde hay replicación física real.
 */
@Testcontainers
class PostgresReplicaProbeIT {

  private static final DbNode NOT_A_REPLICA = new DbNode("NOT_A_REPLICA");
  private static final DbNode UNREACHABLE = new DbNode("UNREACHABLE");
  private static final ReplicaHealthProperties PROPS =
      new ReplicaHealthProperties(
          Duration.ofSeconds(2), Duration.ofSeconds(1), Duration.ofSeconds(5), 2);

  @Container
  static final PostgreSQLContainer<?> STANDALONE = new PostgreSQLContainer<>("postgres:17");

  private static ReplicaDataSources pools;
  private static PostgresReplicaProbe probe;

  @BeforeAll
  static void setUp() {
    Map<DbNode, DataSource> map = new LinkedHashMap<>();
    map.put(
        NOT_A_REPLICA,
        pool(STANDALONE.getJdbcUrl(), STANDALONE.getUsername(), STANDALONE.getPassword()));
    // Puerto 1: nada escucha ahí, la conexión se rechaza.
    map.put(UNREACHABLE, pool("jdbc:postgresql://localhost:1/none", "x", "x"));
    pools = new ReplicaDataSources(map);
    probe = new PostgresReplicaProbe(pools, PROPS);
  }

  @AfterAll
  static void tearDown() throws Exception {
    pools.close();
  }

  @Test
  void primaryOrStandaloneNodeIsNotFitForReads() {
    ReplicaStatus status = probe.probe(NOT_A_REPLICA);

    assertThat(status.state()).isEqualTo(ReplicaState.LAGGING);
    assertThat(status.reason()).isEqualTo("not-in-recovery");
  }

  @Test
  void unreachableNodeIsDownWithinTheProbeTimeout() {
    long start = System.nanoTime();
    ReplicaStatus status = probe.probe(UNREACHABLE);

    assertThat(status.state()).isEqualTo(ReplicaState.DOWN);
    assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(3));
  }

  private static HikariDataSource pool(String url, String user, String password) {
    HikariDataSource ds = new HikariDataSource();
    ds.setJdbcUrl(url);
    ds.setUsername(user);
    ds.setPassword(password);
    ds.setMaximumPoolSize(1);
    ds.setConnectionTimeout(PROPS.probeTimeout().toMillis());
    ds.setValidationTimeout(750);
    return ds;
  }
}
