# Tareas pendientes por microservicio

Revisión del repositorio (rama `develop`, commit `0b058fc`) frente a la consigna del docente y a la documentación del proyecto (README, PRD, TRD, API_CONTRACT, DATABASE_SCHEMA, TEST_PLAN).

> **Nota:** los hallazgos salen de leer el código y la configuración; el sistema no se ha ejecutado todavía. Los marcados como **"probable"** deben confirmarse al ejecutar las pruebas.

**Prioridad:** 🔴 bloquea una prueba obligatoria · 🟠 afecta la nota o la defensa · 🟡 mejora o coherencia

---

## Resumen

| Bloque | Tareas | Complejidad | Pruebas afectadas | Peso en rúbrica |
|---|---|---|---|---|
| [`order-service`](#1-order-service) | 8 | Media-alta | P02–P08 | ~40–55 pts |
| [`inventory-service`](#2-inventory-service) | 7 | Baja | P04 | ~5–15 pts |
| [Infraestructura compartida](#3-infraestructura-compartida-postgresql--docker) | 3 | Baja | P05, P06, P08 | Bloqueante |
| [Documentación y entregables](#4-documentación-y-entregables) | 11 | Baja-media | Todas | 15 pts + requisito de entrega |

**Orden recomendado:** INF-01 → ORD-01 → ORD-02 → ORD-03 → ejecutar P01–P08 y guardar evidencias → resto.

---

## 1. `order-service`

Gestiona pedidos con 2 instancias balanceadas. Contiene el enrutamiento de lecturas/escrituras (`AbstractRoutingDataSource`), el balanceo entre réplicas y la llamada a `inventory-service` vía Eureka.

### Problemas pendientes

#### 🔴 ORD-01 — El failover de `GET /api/orders` devuelve 500 en la primera petición tras la caída (probable)
- **Dónde:** [OrderController.java:72-81](../../src/order-service/src/main/java/com/distribuidos/order/controller/OrderController.java#L72-L81) y `handleReplicaFailure` (líneas 136-142).
- **Causa:** `listOrders` tiene `@Transactional(readOnly = true)`. Cuando `findAll()` falla contra la réplica caída, la excepción atraviesa el proxy transaccional del repositorio y marca la transacción como *rollback-only*. El reintento sobre la otra réplica sí funciona, pero al hacer commit Spring lanza `UnexpectedRollbackException` y el cliente recibe 500.
- **Por qué no lo detectan los tests:** [OrderControllerTest.java:85](../../src/order-service/src/test/java/com/distribuidos/order/controller/OrderControllerTest.java#L85) usa Mockito sin contexto Spring, por lo que no hay transacción real.
- **Solución propuesta:** quitar `@Transactional` del controlador y mover la lectura a un servicio donde cada intento abra su propia transacción (por ejemplo `@Transactional(readOnly = true, propagation = REQUIRES_NEW)` por intento, o `TransactionTemplate`).
- **Hecho cuando:** con `postgres-replica1` detenida, todas las peticiones `GET /api/orders` responden 200 desde `REPLICA_2`, incluida la primera.
- **Prueba:** P06.
- **Estado:** resuelto en `refactor/order-read-routing` (B2) con `RoutedReadExecutor` (una transacción por intento). Medido por el Gateway con `postgres-replica1` detenida: todas las respuestas 200 desde `REPLICA_2`, latencia máxima de unos 1,1 s.

#### 🔴 ORD-02 — La reincorporación no comprueba que la réplica esté "apta"
- **Dónde:** [ReplicaLoadBalancer.java:61-84](../../src/order-service/src/main/java/com/distribuidos/order/datasource/ReplicaLoadBalancer.java#L61-L84).
- **Problema:** solo se comprueba `conn.isValid(2)`. La consigna dice: *"Reincorporar una réplica recuperada **únicamente cuando se encuentre disponible y apta** para atender consultas"*. Una réplica recién arrancada puede aceptar conexiones aunque siga desfasada respecto al primario.
- **Solución propuesta:** en el health check, ejecutar sobre la réplica:
  ```sql
  SELECT pg_is_in_recovery(),
         EXTRACT(EPOCH FROM (now() - pg_last_xact_replay_timestamp())) AS lag_seconds;
  ```
  Reincorporar solo si `pg_is_in_recovery() = true` y `lag_seconds` está por debajo de un umbral configurable (por ejemplo 5 s). Registrar en el log el motivo de cada rechazo.
- **Hecho cuando:** el log muestra que la réplica se reincorpora solo después de alcanzar al primario, y P08 incluye esa evidencia.
- **Prueba:** P08.

#### 🟠 ORD-03 — No se registra en el log el nodo de BD usado en cada operación
- **Dónde:** [OrderController.java](../../src/order-service/src/main/java/com/distribuidos/order/controller/OrderController.java). Solo se registran los fallos.
- **Problema:** la evidencia de P05 exige *"identificación del nodo utilizado, consultas SQL y **registros de aplicación**"*. Hoy el nodo solo aparece en la cabecera HTTP `X-Database-Node`.
- **Solución propuesta:** agregar `log.info("[DB-ROUTING] op={} node={} instance={}", ...)` en escrituras, lecturas ordinarias y lecturas críticas. Opcionalmente, persistir cada operación en la tabla `order_audit_logs`, que ya existe pero no se usa.
- **Hecho cuando:** `docker compose logs order-service-1` muestra la alternancia `REPLICA_1`/`REPLICA_2` y las escrituras en `PRIMARY`.
- **Prueba:** P05, P07.
- **Estado:** resuelto en `refactor/order-read-routing` (B2): logs `db.route` por intento y por escritura, con `cid`, instancia y nodo en cada línea. Falta que `inventory-service` registre el `cid` en sus logs (ya lo recibe en la cabecera) → B4.

#### 🟠 ORD-04 — `GET /api/orders/{id}` no tiene failover
- **Dónde:** [OrderController.java:90-105](../../src/order-service/src/main/java/com/distribuidos/order/controller/OrderController.java#L90-L105).
- **Problema:** sin la cabecera `X-Consistency: critical`, la lectura va a una réplica. Si esa réplica está caída, devuelve error y no prueba con la otra ni marca la réplica como degradada.
- **Solución propuesta:** reutilizar la misma lógica de reintento de ORD-01.
- **Prueba:** P06.
- **Estado:** resuelto en `refactor/order-read-routing` (B2): usa el mismo `RoutedReadExecutor` que el listado.

#### 🟠 ORD-05 — La detección de réplica caída es solo reactiva
- **Dónde:** [ReplicaLoadBalancer.java:61-76](../../src/order-service/src/main/java/com/distribuidos/order/datasource/ReplicaLoadBalancer.java#L61-L76).
- **Problema:** el `@Scheduled` solo revisa las réplicas ya marcadas como degradadas. Una réplica sana que cae solo se detecta cuando una petición de usuario falla contra ella.
- **Solución propuesta:** que el health check programado también revise las réplicas sanas y las marque degradadas antes de que les llegue tráfico. Documentar en el informe el tiempo de detección (≤ 5 s).
- **Prueba:** P06 (tiempo de detección).

#### 🟡 ORD-06 — El precio y el nombre del producto los define el cliente
- **Dónde:** [CreateOrderRequest.java](../../src/order-service/src/main/java/com/distribuidos/order/dto/CreateOrderRequest.java) y `buildOrderItem` en el controlador (línea 192: `"Producto " + productCode`).
- **Problema:** `unitPrice` llega en el body del `POST`, aunque `inventory-service` ya tiene `unit_price` y `name`. En un e-commerce real el cliente no fija el precio.
- **Solución propuesta:** que `/api/inventory/reserve` devuelva `unitPrice` y `name`, y que `order-service` los use. Esto depende de INV-05.

#### 🟡 ORD-07 — No hay compensación de stock si falla la persistencia
- **Problema:** el stock se descuenta en inventario antes de guardar el pedido. Si el `save` en el primario falla, el stock queda reservado sin pedido asociado.
- **Nota:** la consigna dice *"No se exige implementar Sagas ni transacciones distribuidas"*. Basta con **documentarlo como limitación** en el informe y en el video. Mejora opcional: llamar a un endpoint `release` (INV-06) en el bloque `catch`.

#### 🔴 ORD-09 — Una réplica caída sacaba del tráfico a todas las instancias (detectado durante B2)
- **Dónde:** `management` en `order-service` e `inventory-service`; health check del LoadBalancer del Gateway; healthchecks de Compose y Swarm.
- **Causa:** `/actuator/health` incluye el indicador `db` con todos los DataSource, réplicas incluidas. Al caer una réplica, las dos instancias de `order-service` pasaban a `DOWN`, el Gateway las retiraba y respondía 503. En Swarm, las tareas `unhealthy` se reemplazan: habría causado reinicios en bucle.
- **Solución:** el tráfico se decide con `/actuator/health/readiness` (probes de Spring Boot), que no depende de recursos compartidos. `/actuator/health` conserva el detalle de la BD para diagnóstico.
- **Estado:** resuelto en `refactor/order-read-routing` (B2).

#### 🟠 ORD-08 — Faltan tests de integración
- **Problema:** AGENTS.md exige tests unitarios + de integración en CI, y solo existen tests unitarios con mocks.
- **Solución propuesta:** test con Testcontainers (PostgreSQL) que verifique el enrutamiento `PRIMARY`/`REPLICA_*` y el failover con transacciones reales. Este test habría detectado ORD-01.
- **Estado:** resuelto en `refactor/order-read-routing` (B2): `OrderReadRoutingIT` con primario y dos réplicas en Testcontainers, ejecutado con `mvn verify` en el CI de `order-service`. Se amplía en B3.

### Inconsistencias de documentación propias de `order-service`

| Documento | Dice | Realidad |
|---|---|---|
| [API_CONTRACT.md §1](../api-contracts/API_CONTRACT.md) | Cabecera `X-Replication-Lag-Ms` | No implementada. Implementarla con ORD-02 o eliminarla del contrato |
| API_CONTRACT.md §2.1–2.3 | Respuesta con `productCode`, `quantity` y `totalPrice` en el primer nivel | El código devuelve `orderNumber`, `totalAmount`, `customerEmail` e `items[]` |
| API_CONTRACT.md §2.1 | El request no incluye `customerEmail` | El DTO lo acepta (opcional) |
| [TRD.md §1](../trd/TRD.md) | `Spring HTTP Interface / WebClient` | Se usa `RestClient` con `@LoadBalanced` |
| TRD.md §3 | `RoutingDataSourceContextHolder`, `ReplicaManager`, `@ReadFromPrimary`, enrutamiento según `readOnly` | Las clases reales son `RoutingContextHolder` y `ReplicaLoadBalancer`; no existe la anotación y el enrutamiento es explícito en el controlador |
| [DATABASE_SCHEMA.md §2](../database/DATABASE_SCHEMA.md) | `order_audit_logs` para P05/P07 e índice `idx_order_items_product_code` | La tabla no se usa y el índice no existe en `02-init-schemas.sql` |
| DATABASE_SCHEMA.md §6 | "El healthcheck activo marca la réplica UNHEALTHY" | Solo se marca reactivamente (ver ORD-05) |

---

## 2. `inventory-service`

Catálogo de productos y control de stock (1 instancia). Recibe la llamada de `order-service` en P04.

### Problemas pendientes

#### 🟠 INV-01 — No tiene ningún test
- **Dónde:** `src/inventory-service/src/test/` no existe.
- **Problema:** el workflow [ci-inventory-service.yml](../../.github/workflows/ci-inventory-service.yml) ejecuta `mvn test` y pasa sin probar nada.
- **Solución propuesta:**
  - Tests unitarios del controlador: reserva exitosa, stock insuficiente, producto inexistente, parámetros inválidos.
  - Un test de integración con Testcontainers que pruebe el descuento real de stock y el conflicto de `@Version`.

#### 🟠 INV-02 — Entrada sin tipar en `/reserve`
- **Dónde:** [InventoryController.java:35-37](../../src/inventory-service/src/main/java/com/distribuidos/inventory/controller/InventoryController.java#L35-L37).
- **Problema:** `(Integer) request.get("quantity")` lanza `ClassCastException` (500) si `quantity` llega como string (`"2"`) o como decimal (`2.0`).
- **Solución propuesta:** reemplazar `Map<String, Object>` por un `record ReserveStockRequest(String productCode, Integer quantity)` con validación, igual que en `order-service`.

#### 🟠 INV-03 — Las reservas concurrentes del mismo producto devuelven 500
- **Dónde:** [InventoryController.java:61-63](../../src/inventory-service/src/main/java/com/distribuidos/inventory/controller/InventoryController.java#L61-L63) y `@Version` en [InventoryProduct.java:33](../../src/inventory-service/src/main/java/com/distribuidos/inventory/model/InventoryProduct.java#L33).
- **Problema:** cuando dos pedidos reservan el mismo producto a la vez, el segundo lanza `ObjectOptimisticLockingFailureException`, que se convierte en 500. `order-service` lo recibe como "Error al verificar inventario".
- **Solución propuesta:** reintentar 2–3 veces ante el conflicto (`@Retryable` o un bucle), o devolver `409 Conflict` con un mensaje claro. Es relevante en P02, donde se envían 100 peticiones.

#### 🟠 INV-04 — El formato de error no coincide con el contrato
- **Dónde:** [InventoryController.java:40-58](../../src/inventory-service/src/main/java/com/distribuidos/inventory/controller/InventoryController.java#L40-L58).
- **Problema:**
  - El contrato ([API_CONTRACT.md §3.1](../api-contracts/API_CONTRACT.md)) dice que el 400 trae `productCode`, `available: false` y `message`.
  - El código devuelve `error` y `currentStock`, y en "producto no encontrado" no incluye `available`.
  - "Producto no encontrado" debería ser `404`, no `400`.
- **Solución propuesta:** unificar la respuesta en un DTO y actualizar el contrato.

#### 🟡 INV-05 — `/reserve` no devuelve precio ni nombre
- **Problema:** `order-service` no puede obtener el precio real del producto (ver ORD-06).
- **Solución propuesta:** agregar `unitPrice` y `name` a la respuesta 200.

#### 🟡 INV-06 — El historial de movimientos y la liberación de stock no existen
- **Problema:** la tabla `stock_movement_history` existe en el esquema pero no tiene entidad ni se escribe. No hay endpoint para liberar stock (`RELEASE`).
- **Solución propuesta:**
  - Registrar un movimiento `RESERVATION` en cada reserva. Sirve como evidencia de P04.
  - Agregar `POST /api/inventory/release` para la compensación de ORD-07 (opcional).

#### 🟡 INV-07 — Falta la gestión de inventario prometida en el PRD
- **Dónde:** [PRD.md §3](../prd/PRD.md): *"Administrador: consulta y actualiza el stock disponible de productos"*.
- **Problema:** solo existe `GET /api/inventory/{productCode}`. No hay listado de catálogo ni reposición.
- **Solución propuesta:** `GET /api/inventory` (listar productos) y `PUT /api/inventory/{productCode}/stock` (reponer). La alternativa es recortar el PRD para que coincida con lo implementado.

---

## 3. Infraestructura compartida (PostgreSQL / Docker)

No pertenece a ningún microservicio, pero bloquea las pruebas de `order-service`.

#### 🔴 INF-01 — Las réplicas PostgreSQL nunca arrancan
- **Dónde:** [start-replica.sh:7](../../docker/postgres/start-replica.sh#L7). El origen del error es [TRD.md §4](../trd/TRD.md).
- **Causa:** `pg_basebackup -h primary`, pero el servicio se llama `postgres-primary` tanto en [compose.yaml](../../compose.yaml) como en [docker-stack.yml](../../deploy/docker-stack.yml). No hay ningún alias de red. El bucle `until` reintenta indefinidamente.
- **Consecuencia:** `postgres-replica1` y `postgres-replica2` nunca aceptan conexiones, y P05, P06 y P08 no se pueden demostrar.
- **Solución:** cambiar a `-h postgres-primary`. Opcionalmente, añadir un `healthcheck` a las réplicas para que `order-service` espere a que estén listas.
- **Hecho cuando:** `SELECT client_addr, state FROM pg_stat_replication;` en el primario muestra 2 réplicas en estado `streaming`.

#### 🔴 GW-01 — El Gateway corta todas las respuestas (detectado durante B1)
- **Dónde:** [LoggingAndTracingGlobalFilter.java](../../src/api-gateway/src/main/java/com/distribuidos/gateway/filter/LoggingAndTracingGlobalFilter.java) (origen: commit `cbeda3b`).
- **Causa:** el filtro añadía `X-Correlation-Id` y `X-Response-Time-Ms` **después** de que la respuesta ya se había enviado. Las cabeceras eran de solo lectura y se lanzaba `UnsupportedOperationException`.
- **Consecuencia:** el cliente recibía el 200 con el JSON, pero la conexión se cerraba sin terminar la respuesta (`curl` salía con código 18). Las cabeceras de trazabilidad nunca llegaban al cliente, y cada petición dejaba 2 líneas `ERROR` en el log del Gateway.
- **Solución:** registrar las cabeceras con `ServerHttpResponse#beforeCommit` y dejar el log de cierre en `doFinally`. Tests en `LoggingAndTracingGlobalFilterTest`.
- **Estado:** resuelto en la rama `fix/postgres-replica-bootstrap` (B1). El smoke test de Compose lo detectó.

#### 🟡 INF-02 — Falta `.env.example` en la raíz
- **Problema:** `compose.yaml` lee `POSTGRES_USER`, `POSTGRES_PASSWORD` y `REPLICATION_PASSWORD`, pero solo existe [deploy/.env.example](../../deploy/.env.example), que es el de Swarm. La consigna pide documentar las variables de entorno.

#### 🟡 INF-03 — `order-service` e `inventory-service` sin `healthcheck` en Compose
- **Problema:** no hay forma de saber desde `docker compose ps` si los servicios están listos. Sería útil para las evidencias de P03 (estado de los contenedores).

---

## 4. Documentación y entregables

Entregables que exige la consigna y faltan o están incompletos.

| ID | Prioridad | Tarea | Estado actual |
|---|---|---|---|
| DOC-01 | 🔴 | **Informe de pruebas P01–P08** con objetivo, procedimiento, resultado esperado, **resultado obtenido, evidencia y análisis** (incluidos errores transitorios y tiempos de detección) | Solo existe el plan ([TEST_PLAN_P01_P08.md](TEST_PLAN_P01_P08.md)), sin resultados |
| DOC-02 | 🔴 | Carpeta `docs/evidencias/` con salidas de terminal, logs y capturas de cada prueba | No existe |
| DOC-03 | 🔴 | Video de 3–5 minutos con todos los integrantes y su enlace en el README | Marcado como "Pendiente" en [README.md:184](../../README.md#L184) |
| DOC-04 | 🟠 | README: cómo **detener** (`docker compose down`, `down -v`) y **recuperar** componentes | Solo explica cómo iniciar y ver logs |
| DOC-05 | 🟠 | README: comandos para verificar la replicación (`pg_stat_replication` en el primario, `pg_is_in_recovery()` en las réplicas) | No aparecen |
| DOC-06 | 🟠 | TEST_PLAN P06/P08: corregir `docker stop pg-replica1` por `docker stop postgres-replica1` | El comando actual falla |
| DOC-07 | 🟠 | README P03/P06: quitar "100% sin error 500" / "sin errores" y describir el comportamiento real observado (ventana de detección y errores transitorios) | La promesa es demasiado optimista y la consigna pide explicar los fallos |
| DOC-08 | 🟠 | Scripts para P03, P06 y P08 que registren la marca de tiempo de cada petición y calculen el tiempo de recuperación | Solo existe el de P02 |
| DOC-09 | 🟡 | Colección Bruno: agregar stock insuficiente, P03, P06 y P08; en P07 encadenar el `id` devuelto por el `POST` en lugar del `1` fijo | Incompleta |
| DOC-10 | 🟡 | README: listar los datos de prueba (`PROD-001` … `PROD-005`) | Solo están en DATABASE_SCHEMA |
| DOC-11 | 🟡 | Alinear API_CONTRACT, TRD y DATABASE_SCHEMA con el código (ver la tabla de inconsistencias de la sección 1 e INV-04) | Desalineados |

---

## Limitaciones a explicar en la defensa (no requieren código)

- La replicación es asíncrona, así que las réplicas pueden ir algo retrasadas respecto al primario (*replication lag*). Por eso P07 envía las lecturas críticas al primario.
- La lectura crítica depende de que el cliente envíe `X-Consistency: critical`. No hay *read-your-writes* automático.
- No hay promoción automática del primario. La consigna no lo exige.
- No hay compensación de stock (ver ORD-07). La consigna excluye Sagas.
- Detectar una instancia caída en el Gateway tarda hasta el intervalo del health check (5 s) más la caché de Eureka. Algunas peticiones pueden recibir el fallback del Circuit Breaker durante esa ventana.
