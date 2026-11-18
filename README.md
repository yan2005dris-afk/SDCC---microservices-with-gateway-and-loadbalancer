# Sistema Distribuido: Microservicios con Descubrimiento Dinámico, Balanceo de Carga y Persistencia Replicada

[![CI - Eureka Server](https://github.com/yan2005dris-afk/SDCC---microservices-with-gateway-and-loadbalancer/actions/workflows/ci-eureka-server.yml/badge.svg)](https://github.com/yan2005dris-afk/SDCC---microservices-with-gateway-and-loadbalancer/actions/workflows/ci-eureka-server.yml)
[![CI - API Gateway](https://github.com/yan2005dris-afk/SDCC---microservices-with-gateway-and-loadbalancer/actions/workflows/ci-api-gateway.yml/badge.svg)](https://github.com/yan2005dris-afk/SDCC---microservices-with-gateway-and-loadbalancer/actions/workflows/ci-api-gateway.yml)
[![CI - Order Service](https://github.com/yan2005dris-afk/SDCC---microservices-with-gateway-and-loadbalancer/actions/workflows/ci-order-service.yml/badge.svg)](https://github.com/yan2005dris-afk/SDCC---microservices-with-gateway-and-loadbalancer/actions/workflows/ci-order-service.yml)
[![CI - Inventory Service](https://github.com/yan2005dris-afk/SDCC---microservices-with-gateway-and-loadbalancer/actions/workflows/ci-inventory-service.yml/badge.svg)](https://github.com/yan2005dris-afk/SDCC---microservices-with-gateway-and-loadbalancer/actions/workflows/ci-inventory-service.yml)

Proyecto práctico de la asignatura **Aplicaciones Distribuidas (SDCC)** enfocado en diseñar, implementar y validar experimentalmente una arquitectura resiliente y de alta disponibilidad basada en **Spring Boot 3**, **Spring Cloud (Eureka, Gateway, LoadBalancer)**, **PostgreSQL 17 con replicación física por streaming WAL** y orquestación con **Docker Compose**.

---

## 1. Arquitectura del Sistema

El sistema implementa un dominio de comercio electrónico ligero compuesto por dos microservicios de negocio (`order-service` e `inventory-service`), desacoplados mediante el patrón **Database-per-Service** y comunicados por nombres lógicos descubiertos dinámicamente en Eureka.

```text
                                  [ Cliente HTTP / curl / Bruno ]
                                                 │
                                                 ▼ :8080
                                     ┌───────────────────────┐
                                     │  Spring Cloud Gateway │ (Correlation ID + Latencia)
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
          │                                 │                          │
          └─────────────────┬───────────────┘                          │
                            ▼                                          │
              ┌───────────────────────────┐                            │
              │ AbstractRoutingDataSource │                            │
              └─────────────┬─────────────┘                            │
                            │                                          │
            ┌───────────────┼───────────────┐                          │
    (WRITE / CRITICAL_READ) │               │ (ORDINARY READS - R-R)   │ (ISOLATED WRITE)
            ▼               ▼               ▼                          ▼
     ┌─────────────┐ ┌─────────────┐ ┌─────────────┐            ┌─────────────┐
     │  pg-primary │ │ pg-replica1 │ │ pg-replica2 │            │  pg-primary │
     │  Port 5432  │ │  Port 5433  │ │  Port 5434  │            │  (inv_db)   │
     └──────┬──────┘ └─────────────┘ └─────────────┘            └─────────────┘
            │ WAL Streaming      │
            └────────────────────┘
```

### Componentes y Contenedores

| Componente | Contenedor | Puerto Host | Descripción |
|---|---|---|---|
| **API Gateway** | `api-gateway` | `8080` | Punto de entrada reactivo, Circuit Breaker (Resilience4j) y LoadBalancer. |
| **Eureka Server** | `eureka-server` | `8761` | Servidor de descubrimiento y registro de microservicios. |
| **Order Service (Instancia 1)** | `order-service-1` | `8081` | Transaccional, enrutamiento `AbstractRoutingDataSource`. |
| **Order Service (Instancia 2)** | `order-service-2` | `8083` | Instancia balanceada para alta disponibilidad HTTP. |
| **Inventory Service** | `inventory-service` | `8082` | Catálogo de productos y reserva de stock atómica. |
| **PostgreSQL Primary** | `postgres-primary` | `5432` | Nodo maestro para escrituras y emisión de streaming WAL. |
| **PostgreSQL Réplica 1** | `postgres-replica1` | `5433` | Réplica física de solo lectura (`hot_standby = on`). |
| **PostgreSQL Réplica 2** | `postgres-replica2` | `5434` | Réplica física de solo lectura (`hot_standby = on`). |

> **Diagrama Interactivo:** Puedes explorar el diagrama interactivo renderizado con Archify en [docs/architecture/architecture.html](docs/architecture/architecture.html) o el diagrama de persistencia en [docs/architecture/stack-diagram-database.html](docs/architecture/stack-diagram-database.html).

---

## 2. Tecnologías y Librerías

- **Java:** 21 LTS (Eclipse Temurin)
- **Framework:** Spring Boot 3.4.3
- **Ecosistema Cloud:** Spring Cloud 2024.0.0
  - `spring-cloud-starter-netflix-eureka-server` / `eureka-client`
  - `spring-cloud-starter-gateway`
  - `spring-cloud-starter-loadbalancer`
  - `spring-cloud-starter-circuitbreaker-reactor-resilience4j`
- **Persistencia:** Spring Data JPA, Hibernate, HikariCP
- **Base de Datos:** PostgreSQL 17 con streaming físico WAL
- **Orquestación:** Docker & Docker Compose
- **Calidad & CI:** Spotless (Google Java Format), Checkstyle, GitHub Actions

---

## 3. Persistencia Replicada y Enrutamiento Inteligente

Para cumplir con la restricción de que `@Transactional(readOnly = true)` no balancea por sí solo, `order-service` implementa:

1. **`DynamicRoutingDataSource` (extiende `AbstractRoutingDataSource`):**
   - Inyecta un mapa con tres fuentes de datos: `PRIMARY`, `REPLICA_1` y `REPLICA_2`.
   - Utiliza `LazyConnectionDataSourceProxy` para aplazar la adquisición de la conexión física hasta que se determine si la transacción es de escritura o lectura.
2. **`ReplicaLoadBalancer`:**
   - Distribuye las lecturas ordinarias (`GET /api/orders`) alternadamente en **Round-Robin** entre Réplica 1 y Réplica 2.
   - Detecta la caída de una réplica (P06) y desvía transparentemente el tráfico a la réplica sana.
   - Cuenta con un hilo programado (`@Scheduled`) que ejecuta healthchecks activos (`SELECT 1`) y reincorpora automáticamente las réplicas recuperadas al pool de balanceo (P08).
3. **Consistencia de Lectura Crítica (P07):**
   - Si la petición incluye la cabecera `X-Consistency: critical`, la consulta se enruta directamente al nodo `PRIMARY`, evitando inconsistencias derivadas del *replication lag* tras crear un registro.

---

## 4. Instalación y Ejecución

### Prerrequisitos
- [Docker](https://docs.docker.com/engine/install/) 24+
- [Docker Compose](https://docs.docker.com/compose/install/) v2+
- Git

### Paso 1: Clonar el Repositorio
```bash
git clone https://github.com/yan2005dris-afk/SDCC---microservices-with-gateway-and-loadbalancer.git
cd SDCC---microservices-with-gateway-and-loadbalancer
git checkout develop
```

### Paso 2: Levantar el Clúster Completo
```bash
docker compose up -d --build
```

### Paso 3: Verificar Estado de los Contenedores
```bash
docker compose ps
```
Todos los contenedores deben figurar en estado `Up` o `healthy`.

### Paso 4: Visualizar Logs
```bash
# Ver logs de todo el clúster
docker compose logs -f

# Ver logs de un servicio en particular
docker compose logs -f api-gateway
docker compose logs -f order-service-1
```

### Alternativa: Despliegue en Modo Clúster (Docker Swarm)
Si deseas desplegar todo el clúster con alta disponibilidad, red overlay y escalado dinámico en **Docker Swarm**, dispones de los archivos listos en `deploy/`:
```bash
# 1. Construir las imágenes locales para Swarm
./deploy/build-images.sh

# 2. Inicializar Swarm y desplegar el stack
./deploy/deploy-swarm.sh

# 3. Monitorear servicios y réplicas
docker stack services sdcc
docker stack ps sdcc
```
Consulta la guía completa en [deploy/README.md](deploy/README.md).

---

## 5. Pruebas Experimentales Obligatorias (P01 a P08)

El proyecto incluye suites listas para ejecutar en [tests/http/api-tests.http](tests/http/api-tests.http), la colección para [Bruno](tests/bruno/collection) y scripts en `scripts/experiments/`.

| ID | Prueba Experimental | Cómo Verificar / Procedimiento | Resultado Demostrable |
|---|---|---|---|
| **P01** | Descubrimiento Dinámico | `curl -H "Accept: application/json" http://localhost:8761/eureka/apps` o abrir `http://localhost:8761`. | Se observan `API-GATEWAY`, `ORDER-SERVICE` (2 instancias) e `INVENTORY-SERVICE` en estado `UP`. |
| **P02** | Balanceo HTTP (100 reqs) | Ejecutar `./scripts/experiments/test-p02-loadbalance.sh`. | Reparto equitativo (~50% / ~50%) entre `order-service-1:8081` y `order-service-2:8083`. |
| **P03** | Fallo de Instancia | `docker stop order-service-1` y enviar solicitudes a `http://localhost:8080/api/orders`. | El 100% de las peticiones son atendidas por `order-service-2` sin interrupción ni error 500. |
| **P04** | Comunicación Inter-Servicios | Enviar `POST http://localhost:8080/api/orders` con un producto de inventario. | `order-service` invoca a `http://inventory-service/api/inventory/reserve` vía Eureka para validar stock antes de crear. |
| **P05** | Enrutamiento de BD | Realizar un `POST /api/orders` y varios `GET /api/orders`. | El `POST` devuelve `X-Database-Node: PRIMARY`; los `GET` devuelven alternadamente `REPLICA_1` y `REPLICA_2`. |
| **P06** | Fallo de Réplica | Detener una réplica: `docker stop postgres-replica1` y consultar `GET /api/orders`. | El sistema detecta la degradación y envía el 100% de las lecturas a `REPLICA_2` sin errores. |
| **P07** | Consistencia de Lectura | Consultar con cabecera: `GET /api/orders/1` con `X-Consistency: critical`. | Se enruta al nodo `PRIMARY` (`X-Database-Node: PRIMARY`), garantizando consistencia inmediata tras escribir. |
| **P08** | Recuperación de Réplica | Reiniciar la réplica: `docker start postgres-replica1` y esperar 5s. | El healthcheck activo la detecta saludable y la reincorpora automáticamente al balanceo Round-Robin. |

---

## 6. Documentación Adicional

- 📄 [PRD — Product Requirements Document](docs/prd/PRD.md)
- 📄 [TRD — Technical Requirements Document](docs/trd/TRD.md)
- 📄 [Contratos de API REST (OpenAPI / HTTP)](docs/api-contracts/API_CONTRACT.md)
- 📄 [Arquitectura de Base de Datos y Modelo ERD](docs/database/DATABASE_SCHEMA.md)
- 📄 [Guía de Replicación Física WAL PostgreSQL 17](docs/database/REPLICACION_POSTGRESQL_GUIA.md)
- 📄 [Protocolo Detallado de Pruebas P01-P08](docs/plan/TEST_PLAN_P01_P08.md)

---

## 7. Video Demostrativo

- **Enlace al Video:** *(Pendiente de grabación con participación de todos los integrantes)*
- **Duración:** 3 a 5 minutos
- **Contenido del Video:**
  1. Presentación de la arquitectura.
  2. Registro dinámico en Eureka Dashboard.
  3. Demostración de balanceo entre instancias (P02).
  4. Comunicación inter-servicio y validación de stock (P04).
  5. Escritura en Primary y lecturas balanceadas en Réplicas (P05).
  6. Simulación en vivo de fallo de instancia (P03) y fallo de réplica (P06/P08).
  7. Conclusiones y límites del sistema.

---

## 8. Autoría y Equipo de Trabajo

Proyecto desarrollado para la asignatura de **Aplicaciones Distribuidas (SDCC)**:

- **Yandris Rivera Torres** (`yan.2005dris@gmail.com`)
- **Gino Bermúdez**
- **Allison Reyes**
- **Andy Alejandro**

- **Repositorio:** [SDCC---microservices-with-gateway-and-loadbalancer](https://github.com/yan2005dris-afk/SDCC---microservices-with-gateway-and-loadbalancer)
- **Licencia:** MIT
