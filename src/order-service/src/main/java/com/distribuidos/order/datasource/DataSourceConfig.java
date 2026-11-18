package com.distribuidos.order.datasource;

import com.zaxxer.hikari.HikariDataSource;
import java.util.HashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;

@Configuration
public class DataSourceConfig {

  @Value("${DB_PRIMARY_URL:jdbc:postgresql://localhost:5432/orders_db}")
  private String primaryUrl;

  @Value("${DB_REPLICA1_URL:jdbc:postgresql://localhost:5433/orders_db}")
  private String replica1Url;

  @Value("${DB_REPLICA2_URL:jdbc:postgresql://localhost:5434/orders_db}")
  private String replica2Url;

  @Value("${DB_USER:admin}")
  private String dbUser;

  @Value("${DB_PASSWORD:admin_demo_change_me}")
  private String dbPassword;

  private HikariDataSource createDataSource(String url, String poolName) {
    HikariDataSource ds = new HikariDataSource();
    ds.setJdbcUrl(url);
    ds.setUsername(dbUser);
    ds.setPassword(dbPassword);
    ds.setDriverClassName("org.postgresql.Driver");
    ds.setPoolName(poolName);
    ds.setMaximumPoolSize(10);
    ds.setConnectionTimeout(3000);
    ds.setValidationTimeout(2000);
    return ds;
  }

  @Bean
  public DataSource primaryDataSource() {
    return createDataSource(primaryUrl, "HikariPrimaryPool");
  }

  @Bean
  public DataSource replica1DataSource() {
    return createDataSource(replica1Url, "HikariReplica1Pool");
  }

  @Bean
  public DataSource replica2DataSource() {
    return createDataSource(replica2Url, "HikariReplica2Pool");
  }

  @Bean
  @Primary
  public DataSource routingDataSource(
      DataSource primaryDataSource,
      DataSource replica1DataSource,
      DataSource replica2DataSource,
      ReplicaLoadBalancer loadBalancer) {

    loadBalancer.setReplicas(replica1DataSource, replica2DataSource);

    DynamicRoutingDataSource routingDataSource = new DynamicRoutingDataSource();
    Map<Object, Object> targetDataSources = new HashMap<>();
    targetDataSources.put(DataSourceType.PRIMARY, primaryDataSource);
    targetDataSources.put(DataSourceType.REPLICA_1, replica1DataSource);
    targetDataSources.put(DataSourceType.REPLICA_2, replica2DataSource);

    routingDataSource.setTargetDataSources(targetDataSources);
    routingDataSource.setDefaultTargetDataSource(primaryDataSource);
    routingDataSource.afterPropertiesSet();

    // LazyConnectionDataSourceProxy es OBLIGATORIO para diferir la selección de la conexión
    // hasta que Spring JPA determine si la transacción es readOnly o write
    return new LazyConnectionDataSourceProxy(routingDataSource);
  }
}
