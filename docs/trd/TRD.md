# TRD — Technical Requirements Document
## Arquitectura Técnica, Persistencia Replicada y Mecanismos de Distribución

### 1. Pila Tecnológica
- **Lenguaje:** Java 21 LTS
- **Framework:** Spring Boot 3.4.3
- **Cloud Suite:** Spring Cloud 2024.0.0
  - Discovery: Netflix Eureka Server & Client
  - API Gateway: Spring Cloud Gateway (Reactive / WebFlux)
  - Client Load Balancer: Spring Cloud LoadBalancer
  - Inter-service Communication: `RestClient` con `@LoadBalanced` (descubrimiento vía Eureka)
- **Persistencia:** Spring Data JPA / Hibernate, HikariCP
- **Base de Datos:** PostgreSQL 17 (Replicación física streaming WAL)
- **Contenedores:** Docker 29+ y Docker Compose

---

### 2. Topología de Red y Componentes

```text
                                  [ Cliente HTTP / curl / Bruno ]
                                                 │
                                                 ▼ :8080
                                     ┌───────────────────────┐
                                     │  Spring Cloud Gateway │
                                     └───────────┬───────────┘
                                                 │
                           ┌─────────────────────┴─────────────────────┐
                           ▼ :8761                                     │
                 ┌───────────────────┐                                 │
                 │   Eureka Server   │ <─── Service Registry           │
                 └───────────────────┘                                 │
                           │                                           │
          ┌────────────────┴────────────────┐                          │
          ▼                                 ▼                          ▼
┌──────────────────┐              ┌──────────────────┐       ┌────────────────────┐
│ order-service-1  │ (Port 8081)  │ order-service-2  │(8083) │ inventory-service  │ (Port 8082)
└─────────┬────────┘              └─────────┬────────┘       └────────────────────┘
          │                                 │
          └─────────────────┬───────────────┘
                            ▼
              ┌───────────────────────────┐
              │ AbstractRoutingDataSource │
              └─────────────┬─────────────┘
                            │
            ┌───────────────┼───────────────┐
    (WRITE / CRITICAL_READ) │               │ (ORDINARY READS - ROUND ROBIN)
            ▼               ▼               ▼
     ┌─────────────┐ ┌─────────────┐ ┌─────────────┐
     │  pg-primary │ │ pg-replica1 │ │ pg-replica2 │
     │  Port 5432  │ │  Port 5433  │ │  Port 5434  │
     └──────┬──────┘ └─────────────┘ └─────────────┘
            │ WAL Streaming      │
            └────────────────────┘
```

---

### 3. Mecanismo de Persistencia y Enrutamiento de Lecturas/Escrituras

`@Transactional(readOnly = true)` solo define el tipo de transacción, no balancea por sí misma. El nodo que atiende cada lectura lo elige de forma **explícita** un `ReplicaSelector`, y el cambio de conexión lo hace un `AbstractRoutingDataSource`. Es una responsabilidad distinta del balanceo HTTP entre instancias (Spring Cloud LoadBalancer).

#### Componentes (`order-service`)

| Componente | Paquete | Responsabilidad |
|---|---|---|
| `OrderController` | `controller` | Solo HTTP: lee `X-Consistency`, arma `X-Instance-Id` / `X-Database-Node` y devuelve DTO |
| `OrderQueryService` / `OrderCommandService` | `service` | Casos de uso de lectura y de creación de pedidos |
| `RoutedReadExecutor` | `datasource` | Política de lectura: elige el nodo, abre **una transacción nueva por intento** (`readOnly`, `REQUIRES_NEW`) y aplica el failover |
| `ReplicaSelector` → `HealthAwareReplicaSelector` | `datasource` | Round-Robin entre las réplicas **en rotación** según `ReplicaHealthRegistry`; `markDown` retira una réplica en cuanto una petición real falla contra ella |
| `RoutingDataSourceProperties` + `DbNode` | `datasource` | Primario y N réplicas en `sdcc.datasource.replicas` (`name`, `url`), validados al arrancar; `pool-max-size` acota las conexiones por pool |
| `ReplicaHealthMonitor` → `ReplicaProbe` (`PostgresReplicaProbe`) | `datasource.health` | Cada `interval` (2 s) sondea **todas** las réplicas en paralelo (hilos virtuales) por un pool exclusivo de 1 conexión. `UP` = `pg_is_in_recovery()` + `pg_stat_wal_receiver.status = 'streaming'` + lag ≤ `max-lag` (5 s); si responde pero no cumple → `LAGGING`; sin conexión o fuera de `probe-timeout` (1 s) → `DOWN` |
| `ReplicaHealthRegistry` (`ReplicaStatusView`) | `datasource.health` | Estado por réplica con histéresis: sale del balanceo con 1 resultado no `UP`; vuelve tras `rejoin-threshold` (2) sondeos `UP` seguidos (P08). Logs solo en transiciones (`replica.state`, `replica.rotation`) |
| `DatabaseNodesHealthIndicator` | `datasource.health` | Componente `databaseNodes` de `/actuator/health` (reemplaza al indicador `db`): `UP` con `degraded` si falta una réplica; `DOWN` solo si cae el primario |
| `TransientConnectionFailures` | `datasource` | Solo se reintentan los fallos de **conexión** (`SQLTransientConnectionException`, SQLState clase `08`…); un error de SQL o de lógica no se reintenta |
| `DynamicRoutingDataSource` + `LazyConnectionDataSourceProxy` | `datasource` | Enrutan la conexión física según `RoutingContextHolder` (ThreadLocal), que se fija **antes** de la primera sentencia y se limpia siempre |
| `CorrelationIdFilter` / `CorrelationIdPropagationInterceptor` | `observability` | `X-Correlation-Id` en el MDC y en las llamadas a `inventory-service` |

#### Política de lectura
1. **Lectura crítica** (`X-Consistency: critical`, P07) → `PRIMARY`, sin reintento.
2. **Lectura ordinaria** → réplica en rotación elegida por Round-Robin. El sondeo retira una réplica caída en ≤ `interval + probe-timeout` (≈ 3 s) aunque no haya tráfico (P06). Si una petición falla por conexión antes de eso, la réplica se retira al instante y se reintenta en la siguiente, como máximo una vez por réplica. La respuesta incluye `X-Replication-Lag-Ms` (lag del último sondeo) cuando la atiende una réplica.
3. **Sin réplicas sanas** → último recurso en `PRIMARY` (`sdcc.datasource.read.fallback-to-primary`, `true` por defecto). Costo: en ese caso las lecturas cargan al primario. Si está desactivado, o si el primario también falla, se responde `503`.
4. **Escrituras** → `PRIMARY`, en una transacción corta. La llamada a `inventory-service` se hace fuera de la transacción.

#### Presupuesto de tiempos
| Parámetro | Valor | Motivo |
|---|---|---|
| `replica-connection-timeout` | 1 s | Hay otra réplica disponible: conviene fallar rápido |
| `replica-socket-timeout` | 2 s | Corta una consulta contra una réplica que se congeló o cuya IP desapareció (`docker stop`): los paquetes se pierden sin respuesta |
| `primary-connection-timeout` | 3 s | Último recurso: vale la pena esperar más |
| Peor caso de failover | ≈ max(2 s, 0,75 s + 1 s) + lectura | Por debajo del `timelimiter` del Gateway (3 s) |

`spring.jpa.open-in-view: false`: cada transacción usa su propia sesión, y los DTO se materializan dentro de ella.

**Permisos del sondeo:** leer `pg_stat_wal_receiver` requiere superusuario o el rol `pg_monitor`. En la demo, `admin` es superusuario; en producción: `GRANT pg_monitor TO <usuario_app>;`.

---

### 4. Configuración de Replicación PostgreSQL (WAL Streaming)

1. **Nodo Primary:**
   - Configuración en `postgresql.conf`:
     - `wal_level = replica`
     - `max_wal_senders = 10`
     - `wal_keep_size = 256MB`
     - `hot_standby = on`
   - Usuario de replicación: `replicator` con rol `REPLICATION` configurado en `pg_hba.conf`.

2. **Nodos Replica 1 & Replica 2:**
   - Inicializados por `docker/postgres/start-replica.sh` con `pg_basebackup -h "$PRIMARY_HOST" -D /var/lib/postgresql/data -U replicator -P -R -X stream`, donde `PRIMARY_HOST` es el nombre del servicio primario en la red de contenedores (por defecto `postgres-primary`).
   - Healthcheck de contenedor: `SELECT pg_is_in_recovery()` debe devolver `t`; así una réplica solo figura como `healthy` si acepta conexiones y opera como standby.
   - Creación automática de archivo `standby.signal`.
   - Modo `hot_standby = on` activo, permitiendo consultas SQL de solo lectura.

---

### 5. Configuración de Spring Cloud Gateway y LoadBalancer

- **Gateway Port:** `8080`
- **Rutas dinámicas configuradas:**
  ```yaml
  spring:
    cloud:
      gateway:
        routes:
          - id: order-service-route
            uri: lb://order-service
            predicates:
              - Path=/api/orders/**
          - id: inventory-service-route
            uri: lb://inventory-service
            predicates:
              - Path=/api/inventory/**
  ```
- **LoadBalancer Algorithm:** Round-Robin por defecto de Spring Cloud LoadBalancer con verificación de salud reactiva.
