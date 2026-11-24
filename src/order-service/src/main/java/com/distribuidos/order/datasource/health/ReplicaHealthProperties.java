package com.distribuidos.order.datasource.health;

import jakarta.validation.constraints.Min;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Sondeo de salud de las réplicas.
 *
 * @param interval pausa entre ciclos de sondeo. Detección de una caída sin tráfico ≤ {@code
 *     interval + probeTimeout}.
 * @param probeTimeout tiempo máximo de cada sondeo (conexión y consulta).
 * @param maxLag retraso máximo para considerar una réplica apta.
 * @param rejoinThreshold sondeos {@code UP} consecutivos para que una réplica vuelva al balanceo.
 */
@Validated
@ConfigurationProperties("sdcc.datasource.health")
public record ReplicaHealthProperties(
    @DefaultValue("2s") Duration interval,
    @DefaultValue("1s") Duration probeTimeout,
    @DefaultValue("5s") Duration maxLag,
    @DefaultValue("2") @Min(1) int rejoinThreshold) {}
