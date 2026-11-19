package com.distribuidos.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Punto de entrada principal del microservicio de pedidos. */
@SpringBootApplication
@EnableDiscoveryClient
@EnableScheduling
public class OrderServiceApplication {

  /**
   * Inicia el contexto Spring Boot de {@code order-service}.
   *
   * @param args argumentos de línea de comandos.
   */
  public static void main(String[] args) {
    SpringApplication.run(OrderServiceApplication.class, args);
  }
}
