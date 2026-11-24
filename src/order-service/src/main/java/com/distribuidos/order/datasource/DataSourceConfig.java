package com.distribuidos.order.datasource;

import com.zaxxer.hikari.HikariDataSource;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;

/**
 * Pools de conexión del primario y de las réplicas, y {@link DataSource} enrutado que usa JPA.
 *
 * <p>Las réplicas usan tiempos de espera cortos porque, si una falla, hay otra disponible. El
 * primario, que es el último recurso, espera más.
 */
@Configuration
@EnableConfigurationProperties(ReadRoutingProperties.class)
public class DataSourceConfig {

  private static final int MAX_POOL_SIZE = 10;
  private static final long MIN_VALIDATION_TIMEOUT_MS = 250;
  private static final long MAX_VALIDATION_TIMEOUT_MS = 2000;

  private final String primaryUrl;
  private final String replica1Url;
  private final String replica2Url;
  private final String dbUser;
  private final String dbPassword;
  private final ReadRoutingProperties properties;

  /**
   * Crea la configuración a partir de las variables de entorno existentes.
   *
   * @param primaryUrl URL JDBC del primario.
   * @param replica1Url URL JDBC de la réplica 1.
   * @param replica2Url URL JDBC de la réplica 2.
   * @param dbUser usuario de base de datos.
   * @param dbPassword contraseña de base de datos.
   * @param properties tiempos de espera y política de lectura.
   */
  public DataSourceConfig(
      @Value("${DB_PRIMARY_URL:jdbc:postgresql://localhost:5435/orders_db}") String primaryUrl,
      @Value("${DB_REPLICA1_URL:jdbc:postgresql://localhost:5433/orders_db}") String replica1Url,
      @Value("${DB_REPLICA2_URL:jdbc:postgresql://localhost:5434/orders_db}") String replica2Url,
      @Value("${DB_USER:admin}") String dbUser,
      @Value("${DB_PASSWORD:admin_demo_change_me}") String dbPassword,
      ReadRoutingProperties properties) {
    this.primaryUrl = primaryUrl;
    this.replica1Url = replica1Url;
    this.replica2Url = replica2Url;
    this.dbUser = dbUser;
    this.dbPassword = dbPassword;
    this.properties = properties;
  }

  /**
   * Pool del primario: escrituras, lecturas críticas y último recurso de lectura.
   *
   * @return pool del primario.
   */
  @Bean
  public DataSource primaryDataSource() {
    return createPool(primaryUrl, "HikariPrimaryPool", properties.primaryConnectionTimeout(), null);
  }

  /**
   * Pool de la réplica 1.
   *
   * @return pool de la réplica 1.
   */
  @Bean
  public DataSource replica1DataSource() {
    return createPool(
        replica1Url,
        "HikariReplica1Pool",
        properties.replicaConnectionTimeout(),
        properties.replicaSocketTimeout());
  }

  /**
   * Pool de la réplica 2.
   *
   * @return pool de la réplica 2.
   */
  @Bean
  public DataSource replica2DataSource() {
    return createPool(
        replica2Url,
        "HikariReplica2Pool",
        properties.replicaConnectionTimeout(),
        properties.replicaSocketTimeout());
  }

  /**
   * Selector Round-Robin sobre las réplicas, en orden de reparto.
   *
   * @param replica1 pool de la réplica 1.
   * @param replica2 pool de la réplica 2.
   * @return selector de réplicas.
   */
  @Bean
  public RoundRobinReplicaSelector replicaSelector(
      @Qualifier("replica1DataSource") DataSource replica1,
      @Qualifier("replica2DataSource") DataSource replica2) {
    Map<DataSourceType, DataSource> replicas = new LinkedHashMap<>();
    replicas.put(DataSourceType.REPLICA_1, replica1);
    replicas.put(DataSourceType.REPLICA_2, replica2);
    return new RoundRobinReplicaSelector(replicas);
  }

  /**
   * {@link DataSource} enrutado que usa JPA. Sin nodo explícito en el contexto, usa el primario.
   *
   * <p>{@link LazyConnectionDataSourceProxy} es obligatorio: difiere la obtención de la conexión
   * física hasta la primera sentencia, cuando el nodo ya está fijado en {@link
   * RoutingContextHolder}.
   *
   * @param primary pool del primario.
   * @param replica1 pool de la réplica 1.
   * @param replica2 pool de la réplica 2.
   * @return {@link DataSource} enrutado.
   */
  @Bean
  @Primary
  public DataSource routingDataSource(
      @Qualifier("primaryDataSource") DataSource primary,
      @Qualifier("replica1DataSource") DataSource replica1,
      @Qualifier("replica2DataSource") DataSource replica2) {
    Map<Object, Object> targets = new LinkedHashMap<>();
    targets.put(DataSourceType.PRIMARY, primary);
    targets.put(DataSourceType.REPLICA_1, replica1);
    targets.put(DataSourceType.REPLICA_2, replica2);

    DynamicRoutingDataSource routing = new DynamicRoutingDataSource();
    routing.setTargetDataSources(targets);
    routing.setDefaultTargetDataSource(primary);
    routing.afterPropertiesSet();
    return new LazyConnectionDataSourceProxy(routing);
  }

  private HikariDataSource createPool(
      String url, String poolName, Duration connectionTimeout, Duration socketTimeout) {
    HikariDataSource ds = new HikariDataSource();
    ds.setJdbcUrl(url);
    ds.setUsername(dbUser);
    ds.setPassword(dbPassword);
    ds.setDriverClassName("org.postgresql.Driver");
    ds.setPoolName(poolName);
    ds.setMaximumPoolSize(MAX_POOL_SIZE);
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
