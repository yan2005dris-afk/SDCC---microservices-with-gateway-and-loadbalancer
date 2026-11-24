package com.distribuidos.order.datasource;

import com.distribuidos.order.datasource.health.ReplicaHealthProperties;
import com.zaxxer.hikari.HikariDataSource;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;

/**
 * Pools de conexión del primario y de las réplicas declaradas en {@code sdcc.datasource}, y {@link
 * DataSource} enrutado que usa JPA.
 *
 * <p>Las réplicas usan tiempos de espera cortos porque, si una falla, hay otra disponible. El
 * primario, que es el último recurso, espera más.
 */
@Configuration
@EnableConfigurationProperties({
  RoutingDataSourceProperties.class,
  ReadRoutingProperties.class,
  ReplicaHealthProperties.class
})
public class DataSourceConfig {

  private static final long MIN_VALIDATION_TIMEOUT_MS = 250;
  private static final long MAX_VALIDATION_TIMEOUT_MS = 2000;

  private final RoutingDataSourceProperties nodes;
  private final ReadRoutingProperties timeouts;

  /**
   * Crea la configuración.
   *
   * @param nodes primario, réplicas y credenciales.
   * @param timeouts tiempos de espera y política de lectura.
   */
  public DataSourceConfig(RoutingDataSourceProperties nodes, ReadRoutingProperties timeouts) {
    this.nodes = nodes;
    this.timeouts = timeouts;
  }

  /**
   * Pool del primario: escrituras, lecturas críticas y último recurso de lectura.
   *
   * @return pool del primario.
   */
  @Bean
  public HikariDataSource primaryDataSource() {
    return createPool(
        nodes.primary().url(),
        "HikariPrimaryPool",
        nodes.poolMaxSize(),
        timeouts.primaryConnectionTimeout(),
        null);
  }

  /**
   * Pools de tráfico de cada réplica, en orden de reparto.
   *
   * @return pools de réplicas; se cierran al apagar la aplicación.
   */
  @Bean
  public ReplicaDataSources replicaDataSources() {
    Map<DbNode, DataSource> pools = new LinkedHashMap<>();
    for (RoutingDataSourceProperties.Replica replica : nodes.replicas()) {
      pools.put(
          replica.node(),
          createPool(
              replica.url(),
              "Hikari" + replica.name() + "Pool",
              nodes.poolMaxSize(),
              timeouts.replicaConnectionTimeout(),
              timeouts.replicaSocketTimeout()));
    }
    return new ReplicaDataSources(pools);
  }

  /**
   * Pools exclusivos del sondeo de salud: 1 conexión por réplica, separada del tráfico, para que un
   * pool de tráfico agotado no se confunda con una réplica caída.
   *
   * @param health configuración del sondeo.
   * @return pools de sondeo; se cierran al apagar la aplicación.
   */
  @Bean
  public ReplicaDataSources replicaProbeDataSources(ReplicaHealthProperties health) {
    Map<DbNode, DataSource> pools = new LinkedHashMap<>();
    for (RoutingDataSourceProperties.Replica replica : nodes.replicas()) {
      pools.put(
          replica.node(),
          createPool(
              replica.url(),
              "HikariProbe" + replica.name() + "Pool",
              1,
              health.probeTimeout(),
              health.probeTimeout()));
    }
    return new ReplicaDataSources(pools);
  }

  /**
   * {@link DataSource} enrutado que usa JPA. Sin nodo explícito en el contexto, usa el primario.
   *
   * <p>{@link LazyConnectionDataSourceProxy} es obligatorio: difiere la obtención de la conexión
   * física hasta la primera sentencia, cuando el nodo ya está fijado en {@link
   * RoutingContextHolder}.
   *
   * @param primary pool del primario.
   * @param replicas pools de tráfico de las réplicas.
   * @return {@link DataSource} enrutado.
   */
  @Bean
  @Primary
  public DataSource routingDataSource(
      @Qualifier("primaryDataSource") DataSource primary,
      @Qualifier("replicaDataSources") ReplicaDataSources replicas) {
    Map<Object, Object> targets = new LinkedHashMap<>();
    targets.put(DbNode.PRIMARY, primary);
    targets.putAll(replicas.asMap());

    DynamicRoutingDataSource routing = new DynamicRoutingDataSource();
    routing.setTargetDataSources(targets);
    routing.setDefaultTargetDataSource(primary);
    routing.afterPropertiesSet();
    return new LazyConnectionDataSourceProxy(routing);
  }

  private HikariDataSource createPool(
      String url,
      String poolName,
      int maxPoolSize,
      Duration connectionTimeout,
      Duration socketTimeout) {
    HikariDataSource ds = new HikariDataSource();
    ds.setJdbcUrl(url);
    ds.setUsername(nodes.username());
    ds.setPassword(nodes.password());
    ds.setDriverClassName("org.postgresql.Driver");
    ds.setPoolName(poolName);
    ds.setMaximumPoolSize(maxPoolSize);
    ds.setConnectionTimeout(connectionTimeout.toMillis());
    // Hikari exige validationTimeout < connectionTimeout.
    ds.setValidationTimeout(
        Math.max(
            MIN_VALIDATION_TIMEOUT_MS,
            Math.min(MAX_VALIDATION_TIMEOUT_MS, connectionTimeout.toMillis() * 3 / 4)));
    if (socketTimeout != null) {
      // Propiedad del driver PostgreSQL (segundos): corta consultas contra un nodo congelado.
      ds.addDataSourceProperty("socketTimeout", Math.max(1, socketTimeout.toSeconds()));
    }
    return ds;
  }
}
