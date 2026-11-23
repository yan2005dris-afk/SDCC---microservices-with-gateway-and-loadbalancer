# TRD — Technical Requirements Document
## Arquitectura Técnica, Persistencia Replicada y Mecanismos de Distribución

### 1. Pila Tecnológica
- **Lenguaje:** Java 21 LTS
- **Framework:** Spring Boot 3.4.3
- **Cloud Suite:** Spring Cloud 2024.0.0
  - Discovery: Netflix Eureka Server & Client
  - API Gateway: Spring Cloud Gateway (Reactive / WebFlux)
  - Client Load Balancer: Spring Cloud LoadBalancer
  - Inter-service Communication: Spring HTTP Interface / WebClient
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

Para cumplir con la restricción de que `@Transactional(readOnly = true)` no balancea por sí solo, se implementa una arquitectura basada en **`AbstractRoutingDataSource`** con un **`ReplicaRoundRobinLoadBalancer`** y health checking pasivo/activo.

#### Componentes de la Capa de Datos:
1. **`DataSourceType` (Enum):**
   - `PRIMARY`
   - `REPLICA_1`
   - `REPLICA_2`

2. **`RoutingDataSourceContextHolder` (ThreadLocal):**
   - Almacena el target datasource seleccionado para la transacción o petición actual.
   - Si la transacción no es de solo lectura (`@Transactional` por defecto), se fija en `PRIMARY`.
   - Si es de solo lectura (`@Transactional(readOnly = true)`), se invoca al balanceador de réplicas.

3. **`DynamicRoutingDataSource` (Extiende `AbstractRoutingDataSource`):**
   - Inyecta un mapa de datasources:
     - Clave `PRIMARY` -> `HikariDataSource` apuntando a `postgres-primary:5432`.
     - Clave `REPLICA_1` -> `HikariDataSource` apuntando a `postgres-replica1:5432`.
     - Clave `REPLICA_2` -> `HikariDataSource` apuntando a `postgres-replica2:5432`.
   - Implementa `determineCurrentLookupKey()` extrayendo el valor del context holder.

4. **`ReplicaManager` con Failover:**
   - Mantiene una lista circular de réplicas saludables (`replica1`, `replica2`).
   - Si una réplica falla al intentar abrir conexión o falla su healthcheck, es marcada temporalmente como degradada y el tráfico se redirige a la réplica superviviente.
   - Un hilo programado de verificación (`@Scheduled(fixedDelay = 5000)`) prueba la conectividad con `pg_isready` o `SELECT 1` y la reincorpora al pool cuando vuelve a estar disponible (P08).

5. **Anotación `@ReadFromPrimary` / Cabecera `X-Consistency: critical`:**
   - Permite forzar explícitamente lectura en el Primary para consultas inmediatamente posteriores a escrituras (P07).

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
