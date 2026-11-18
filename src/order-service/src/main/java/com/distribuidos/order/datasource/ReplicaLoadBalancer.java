package com.distribuidos.order.datasource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class ReplicaLoadBalancer {

    private static final Logger log = LoggerFactory.getLogger(ReplicaLoadBalancer.class);

    private final AtomicInteger counter = new AtomicInteger(0);
    private final AtomicBoolean replica1Healthy = new AtomicBoolean(true);
    private final AtomicBoolean replica2Healthy = new AtomicBoolean(true);

    private DataSource replica1DataSource;
    private DataSource replica2DataSource;

    public void setReplicas(DataSource replica1, DataSource replica2) {
        this.replica1DataSource = replica1;
        this.replica2DataSource = replica2;
    }

    public DataSourceType getNextReplica() {
        boolean r1 = replica1Healthy.get();
        boolean r2 = replica2Healthy.get();

        if (r1 && r2) {
            // Round Robin entre las dos réplicas saludables
            int current = counter.getAndIncrement();
            return (Math.abs(current) % 2 == 0) ? DataSourceType.REPLICA_1 : DataSourceType.REPLICA_2;
        } else if (r1) {
            return DataSourceType.REPLICA_1;
        } else if (r2) {
            return DataSourceType.REPLICA_2;
        } else {
            // Si ambas réplicas cayeron, failover de emergencia hacia el Primary
            log.warn("Ambas réplicas no saludables. Failover automático a PRIMARY");
            return DataSourceType.PRIMARY;
        }
    }

    public void markReplicaDegraded(DataSourceType type) {
        if (type == DataSourceType.REPLICA_1) {
            if (replica1Healthy.compareAndSet(true, false)) {
                log.warn("REPLICA_1 marcada como NO SALUDABLE (Fallo de conexión)");
            }
        } else if (type == DataSourceType.REPLICA_2) {
            if (replica2Healthy.compareAndSet(true, false)) {
                log.warn("REPLICA_2 marcada como NO SALUDABLE (Fallo de conexión)");
            }
        }
    }

    // Healthcheck activo para P08 (Reincorporación automática de réplicas)
    @Scheduled(fixedDelay = 5000)
    public void checkReplicaHealth() {
        if (replica1DataSource != null && !replica1Healthy.get()) {
            if (testConnection(replica1DataSource)) {
                replica1Healthy.set(true);
                log.info("P08: REPLICA_1 RECUPERADA y reintegrada al balanceo de lecturas.");
            }
        }

        if (replica2DataSource != null && !replica2Healthy.get()) {
            if (testConnection(replica2DataSource)) {
                replica2Healthy.set(true);
                log.info("P08: REPLICA_2 RECUPERADA y reintegrada al balanceo de lecturas.");
            }
        }
    }

    private boolean testConnection(DataSource ds) {
        try (Connection conn = ds.getConnection()) {
            return conn.isValid(2);
        } catch (Exception e) {
            return false;
        }
    }
}
