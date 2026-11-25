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
┌──────────────────────────┐           ┌──────────────────────────┐       ┌──────────────────────────┐
│ order-service-1          │           │ order-service-2          │       │ inventory-service        │
│ host :8081 → ctr :8081   │           │ host :8083 → ctr :8081   │       │ host/ctr :8082           │
└──────────┬───────────────┘           └──────────┬───────────────┘       └──────────────────────────┘
           │                                       │                              │
           └─────────────────┬─────────────────────┘                              │
                            ▼                                          │
              ┌───────────────────────────┐                            │
              │ AbstractRoutingDataSource │                            │
              └─────────────┬─────────────┘                            │
                            │                                          │
            ┌───────────────┼───────────────┐                          │
    (WRITE / CRITICAL_READ) │               │ (ORDINARY READS - R-R)   │ (ISOLATED WRITE)
            ▼               ▼               ▼                          ▼
          ┌─────────────────────────────┐
          │ postgres-primary            │
          │ container :5432 / host :5435│
          │ orders_db + inventory_db    │
          └──────────────┬──────────────┘
                         │ WAL streaming
               ┌─────────┴─────────┐
               ▼                   ▼
     ┌────────────────────┐ ┌────────────────────┐
     │ postgres-replica1  │ │ postgres-replica2  │
     │ container :5432    │ │ container :5432    │
     │ host :5433          │ │ host :5434          │
     └────────────────────┘ └────────────────────┘
```

### Componentes y Contenedores

| Componente | Contenedor | Puerto Host (contenedor) | Descripción |
|---|---|---|---|
| **API Gateway** | `api-gateway` | `8080` (`8080`) | Punto de entrada reactivo, Circuit Breaker (Resilience4j) y LoadBalancer. |
| **Eureka Server** | `eureka-server` | `8761` (`8761`) | Servidor de descubrimiento y registro de microservicios. |
| **Order Service (Instancia 1)** | `order-service-1` | `8081` (`8081`) | Transaccional, enrutamiento `AbstractRoutingDataSource`. |
| **Order Service (Instancia 2)** | `order-service-2` | `8083` (`8081`) | Instancia balanceada; el ID Eureka `order-service-2:8083` es un identificador, no el puerto del contenedor. |
| **Inventory Service** | `inventory-service` | `8082` (`8082`) | Catálogo paginado de productos, reserva de stock atómica y reposición de stock. |
| **PostgreSQL Primary** | `postgres-primary` | `5435` (`5432`, configurable con `PG_PRIMARY_PORT`) | Nodo maestro para escrituras y emisión de streaming WAL. |
| **PostgreSQL Réplica 1** | `postgres-replica1` | `5433` (`5432`) | Réplica física de solo lectura (`hot_standby = on`). |
| **PostgreSQL Réplica 2** | `postgres-replica2` | `5434` (`5432`) | Réplica física de solo lectura (`hot_standby = on`). |

> **Diagrama Interactivo:** Puedes explorar el diagrama interactivo renderizado con Archify en [docs/architecture/architecture.html](docs/architecture/architecture.html) o el diagrama de persistencia en [docs/architecture/stack-diagram-database.html](docs/architecture/stack-diagram-database.html).

### API de Inventario (sin autenticación)

> **Ninguno de estos endpoints requiere autenticación ni credenciales.** Es una herramienta interna del
> proyecto y así se declara en la invariante "sin autenticación" del plan de implementación. El
> endpoint de reposición escribe stock real, por lo que su exposición debe limitarse a la red interna
> del clúster (no publicarlo directamente en Internet).

| Método y ruta | Descripción | Respuesta |
|---|---|---|
| `GET /api/inventory?page=0&size=20` | Catálogo paginado de productos. `size` máximo 100 (se trunca a 100 si se pide más), orden por defecto `productCode ASC`; un `sort` con una propiedad desconocida se descarta. | `{ content, totalElements, totalPages, currentPage, size }` |
| `GET /api/inventory/{productCode}` | Detalle de un producto por código. | `200` con el producto · `404` si no existe |
| `POST /api/inventory/reserve` | Reserva stock para un pedido (idempotente por `orderNumber`). | `200` con el resultado de la reserva · `400` si no hay stock |
| `POST /api/inventory/release` | Devuelve el stock de una reserva (idempotente). | `200` con el resultado de la liberación |
| `PUT /api/inventory/{productCode}/stock` | **Reposición atómica.** Body `{"quantity": 25}` (`quantity` > 0). Incrementa el stock y registra un movimiento `RESTOCK` en la **misma transacción**. | `200` con `{ productCode, quantity, stock, message }` · `400` si `quantity` falta o no es positivo · `404` si el producto no existe |

El tope de items por página del catálogo es configurable con la propiedad
`inventory.pagination.max-page-size` (variable de entorno `INVENTORY_MAX_PAGE_SIZE`, valor por
defecto `100`).

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
   - Cuenta con sondeos activos que consideran apta una réplica solo si está en recuperación, el receptor WAL está en `streaming` y el lag medido es conocido y no supera el umbral configurado (5 s por defecto). Un fallo la saca de rotación; vuelve tras dos sondeos sanos consecutivos (P06/P08).
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

### Paso 2: Preparar configuración local
```bash
test -e .env || cp .env.example .env
```
`.env.example` contiene valores ficticios para pruebas locales. Si modificas `.env`, no lo publiques ni lo agregues al repositorio.

### Paso 3: Levantar el Clúster y esperar salud
```bash
docker compose up -d --build --wait --wait-timeout 300
```

### Paso 4: Verificar contenedores, semillas y replicación
```bash
docker compose ps
docker compose exec -T postgres-primary sh -lc 'psql -U "$POSTGRES_USER" -d inventory_db -c "SELECT product_code, stock, reserved_stock FROM inventory_products ORDER BY product_code;"'
docker compose exec -T postgres-primary sh -lc 'psql -U "$POSTGRES_USER" -d postgres -c "SELECT application_name, state, sync_state FROM pg_stat_replication ORDER BY application_name;"'
```
Espera a que los servicios con healthcheck estén `healthy`. La consulta de inventario muestra los productos de semilla; la consulta del primario debe mostrar las dos réplicas en `streaming` (replicación asíncrona). No interpretes `Up` por sí solo como disponibilidad funcional.

### Paso 5: Visualizar Logs
```bash
# Ver logs de todo el clúster
docker compose logs -f

# Ver logs de un servicio en particular
docker compose logs -f api-gateway
docker compose logs -f order-service-1
```

### Apagar o recuperar componentes sin borrar datos
Para detener el stack local y conservar los volúmenes, usa `docker compose stop`. **No uses `docker compose down -v`**. Después de una prueba de fallo, restaura cualquier componente detenido y comprueba su salud antes de continuar:
```bash
docker start order-service-1
docker start postgres-replica1
docker compose ps
```
El healthcheck del contenedor réplica confirma `pg_is_in_recovery()`. El reingreso al balanceador de `order-service` exige además receptor WAL `streaming`, lag conocido dentro del límite y dos sondeos sanos consecutivos; verifica lecturas con `X-Database-Node` y, en el primario, el estado `streaming` de `pg_stat_replication`.

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
| **P02** | Balanceo HTTP (100 reqs) | Confirma ambas instancias `UP` en Eureka y una respuesta HTTP 200 desde cada una a través del Gateway; `UP` por sí solo no confirma que la lista del Gateway se haya propagado. Si hay 503, registra la ventana de arranque y espera la respuesta de ambas antes de medir. Ejecuta `bash scripts/experiments/test-p02-loadbalance.sh`. | El script registra cada código HTTP, instancia, nodo y error; evalúa la distribución observada de las 100 respuestas, sin asumir un reparto exacto. |
| **P03** | Fallo de Instancia | `docker stop --timeout 40 order-service-1`, envía la tanda del protocolo y luego restaura con `docker start order-service-1`. | Se registran cantidad, códigos HTTP e instancia de cada solicitud durante el fallo y tras restaurar; no se presupone éxito del 100% sin medirlo. |
| **P04** | Comunicación Inter-Servicios | Enviar `POST http://localhost:8080/api/orders` con un producto de inventario. | `order-service` invoca a `http://inventory-service/api/inventory/reserve` vía Eureka para validar stock antes de crear. |
| **P05** | Enrutamiento de BD | Crear un pedido y hacer varios `GET /api/orders`; registra cabeceras. | Comprueba `PRIMARY` en la escritura y mide los nodos devueltos en las lecturas; no presupone alternancia por petición. |
| **P06** | Fallo de Réplica | `docker stop --timeout 40 postgres-replica1`, consulta `GET /api/orders` y luego restaura con `docker start postgres-replica1`. | Registra respuestas, cabeceras y errores durante el fallo; la réplica restante debe ser la única elegible cuando se detecte la caída. |
| **P07** | Consistencia de Lectura | Usa el ID devuelto por el `POST`: `GET /api/orders/{id}` con `X-Consistency: critical`. | Comprueba que la petición devuelve el mismo pedido con `X-Database-Node: PRIMARY`; no afirma garantías más amplias que lo observado. |
| **P08** | Recuperación de Réplica | `docker start postgres-replica1`; mide hasta salud, WAL `streaming` y reincorporación a lecturas. | Registra el tiempo medido y las cabeceras tras reingreso; la salud Compose por sí sola no prueba que la réplica esté en rotación. |

---

## 6. Documentación Adicional

- 📄 [PRD — Product Requirements Document](docs/prd/PRD.md)
- 📄 [TRD — Technical Requirements Document](docs/trd/TRD.md)
- 📄 [Contratos de API REST (OpenAPI / HTTP)](docs/api-contracts/API_CONTRACT.md)
- 📄 [Arquitectura de Base de Datos y Modelo ERD](docs/database/DATABASE_SCHEMA.md)
- 📄 [Guía de Replicación Física WAL PostgreSQL 17](docs/database/REPLICACION_POSTGRESQL_GUIA.md)
- 📄 [Protocolo Detallado de Pruebas P01-P08](docs/plan/TEST_PLAN_P01_P08.md)
- 📄 [Informe experimental P01-P08 y evidencias](docs/plan/INFORME_P01_P08.md)
- 🎬 [Guion del video demostrativo](docs/plan/GUION_VIDEO.md)

---

## 7. Video Demostrativo

- **Enlace al Video:** *(Pendiente de grabación con participación de todos los integrantes)*
- **Duración:** 3 a 5 minutos
- **Guion y lista de tomas:** [docs/plan/GUION_VIDEO.md](docs/plan/GUION_VIDEO.md)
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
