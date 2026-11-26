package com.distribuidos.order.loadbalancer;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Cliente HTTP hacia {@code inventory-service}.
 *
 * <p>Presupuesto: {@code connectTimeout + readTimeout} + escritura del pedido debe quedar por
 * debajo del {@code timelimiter} del Gateway (3 s); si el Gateway cortara antes, la compensación no
 * llegaría a ejecutarse y el cliente vería un error aunque el pedido existiera.
 *
 * @param baseUrl URL lógica (resuelta por Spring Cloud LoadBalancer).
 * @param connectTimeout tiempo máximo para conectar.
 * @param readTimeout tiempo máximo de espera de la respuesta.
 */
@ConfigurationProperties("sdcc.inventory")
public record InventoryClientProperties(
    @DefaultValue("http://inventory-service") String baseUrl,
    @DefaultValue("500ms") Duration connectTimeout,
    @DefaultValue("1500ms") Duration readTimeout) {}
