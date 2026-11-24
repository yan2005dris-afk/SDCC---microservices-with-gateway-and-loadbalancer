package com.distribuidos.order.datasource;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuración del enrutamiento de lecturas y de los tiempos de espera de cada pool.
 *
 * <p>Presupuesto de tiempo: un failover cuesta como máximo {@code replicaConnectionTimeout} más la
 * lectura en la siguiente réplica, y debe quedar por debajo del timelimiter del Gateway (3 s).
 *
 * @param fallbackToPrimary si no queda ninguna réplica sana, leer del primario (último recurso).
 * @param replicaConnectionTimeout espera máxima por una conexión de réplica (fallar rápido).
 * @param replicaSocketTimeout corta una consulta contra una réplica que dejó de responder.
 * @param primaryConnectionTimeout espera máxima por una conexión del primario.
 */
@ConfigurationProperties("sdcc.datasource.read")
public record ReadRoutingProperties(
    @DefaultValue("true") boolean fallbackToPrimary,
    @DefaultValue("1s") Duration replicaConnectionTimeout,
    @DefaultValue("2s") Duration replicaSocketTimeout,
    @DefaultValue("3s") Duration primaryConnectionTimeout) {}
