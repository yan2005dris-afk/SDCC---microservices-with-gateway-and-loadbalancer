package com.distribuidos.order.loadbalancer;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** {@link RestClient} hacia {@code inventory-service}: balanceado, con timeouts y correlación. */
@Configuration
@EnableConfigurationProperties(InventoryClientProperties.class)
public class InventoryClientConfig {

  /**
   * Cliente HTTP de inventario.
   *
   * @param builder builder balanceado (con propagación de {@code X-Correlation-Id}).
   * @param properties URL y timeouts.
   * @return cliente.
   */
  @Bean
  @Qualifier("inventoryRestClient")
  public RestClient inventoryRestClient(
      @LoadBalanced RestClient.Builder builder, InventoryClientProperties properties) {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(properties.connectTimeout());
    requestFactory.setReadTimeout(properties.readTimeout());
    // clone(): el builder balanceado es compartido; no se modifica para otros clientes.
    return builder.clone().baseUrl(properties.baseUrl()).requestFactory(requestFactory).build();
  }
}
