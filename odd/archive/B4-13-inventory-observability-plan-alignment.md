# ODD — B4-13: Observabilidad y alineación del plan en `inventory-service`

**Rama:** `brydyan/feature/inventory-reserve-hardening`
**Depende de:** B4-01 … B4-12 (bloque B4 completo, todas en PASS y archivadas)
**Responsable:** Andy

> Cierra los ítems del checklist de B4 de `docs/plan/PLAN_IMPLEMENTACION.md` que **nunca** llegaron a
> convertirse en ODD (ni en tarea de ninguna ODD del bloque) y que seguían sin implementarse.

---

## Spec

### S1 — `X-Instance-Id` por filtro, no por método

El plan lo pide explícitamente: *"se agrega con un filtro en todas las respuestas, no en cada método"*.
Hoy `InventoryController` pone la cabecera a mano en los tres métodos (`getProduct`, `reserveStock`,
`releaseStock`): cualquier endpoint nuevo la olvidaría.

**Hecho cuando:**
- Un filtro pone `X-Instance-Id` en **todas** las respuestas del servicio, incluidos actuator y errores.
- El controller deja de poner la cabecera a mano (una sola fuente de verdad).
- Un test demuestra que una ruta sin mapping también lleva la cabecera.

### S2 — `CorrelationIdFilter` (MDC) con el mismo patrón que `order-service`

`order-service` ya tiene `CorrelationIdFilter` + `InstanceIdentity` + `MdcKeys`: toma el `X-Correlation-Id`
del Gateway (o genera uno si la petición llega directa), lo deja en el MDC, lo devuelve en la respuesta y
limpia el MDC al terminar. `inventory-service` no tiene nada de eso.

**Hecho cuando:**
- `inventory-service` tiene `observability/MdcKeys`, `observability/InstanceIdentity` y
  `observability/CorrelationIdFilter` con el **mismo** patrón y los mismos nombres de clave.
- El MDC se limpia en el `finally` (Tomcat reutiliza hilos: si no, el `cid` se filtra entre peticiones).
- `logging.pattern.level` incluye `cid` e `inst`.

### S3 — Log estructurado de la reserva

El plan pide la línea: `stock.reserve code=... qty=... order=... result=OK|INSUFFICIENT remaining=...`.

**Hecho cuando:**
- Una reserva exitosa deja `INFO` con `result=OK` y el `remaining` resultante.
- Un rechazo por stock deja `INFO` con `result=INSUFFICIENT` y el stock disponible.
- El log **no** incluye stack trace ni datos sensibles.

### S4 — `spring.jpa.open-in-view: false`

Hoy está en el valor por defecto (`true`), y Hibernate avisa en cada arranque.

**Hecho cuando:**
- `application.yml` fija `spring.jpa.open-in-view: false`.
- El arranque no emite el warning `spring.jpa.open-in-view is enabled by default`.
- La serialización de `GET /api/inventory/{productCode}` sigue funcionando (la entidad no tiene relaciones
  lazy: `id`, `productCode`, `name`, `description`, `stock`, `reservedStock`, `unitPrice`, `version`,
  `createdAt`, `updatedAt` — todos campos planos).

### S5 — Índice `idx_stock_movement_product_code`

El plan lo pide y no existe. El plan además nombra `uq_stock_movement_ref_type`, un índice **obsoleto**: el
script de migración lo dropea y crea `uq_stock_movement_order_product_type` (índice parcial sobre
`(reference_order_number, movement_type, product_code)`).

**Hecho cuando:**
- `docker/postgres/02-init-schemas.sql` crea `idx_stock_movement_product_code` sobre `product_code`.
- `docker/postgres/migrate-stock-movement-index.sql` lo crea en BDs ya inicializadas.
- El esquema de test (`inventory-schema.sql`) lo replica.
- Un IT verifica que el índice existe en Postgres real.

### S6 — Test de contención con stock escaso

El checklist del plan pide: *"Producto con stock 5 y 20 hilos que reservan 1 unidad cada uno → exactamente
5 éxitos, stock final 0, 5 movimientos `RESERVATION`"*. No estaba cubierto: el IT existente drena con
stock = 20 y 20 éxitos.

**Hecho cuando:**
- Con stock 5 y 20 peticiones concurrentes: exactamente **5** respuestas 200 y **15** 400 por stock
  insuficiente, stock final 0, `reserved_stock` 5, **5** movimientos `RESERVATION` (los 15 rechazos no
  dejan movimiento: la transacción revierte).

### S7 — Trazabilidad del plan al día

**Hecho cuando:**
- `docs/plan/PLAN_IMPLEMENTACION.md`: checklist de B4 marcado, `INV-01`…`INV-06` marcados, bloque B4
  marcado en la tabla de ramas, y el nombre obsoleto del índice corregido.
- `docs/plan/TAREAS_PENDIENTES.md`: las entradas de INV-01…INV-06 quedan marcadas como resueltas, con
  la evidencia de dónde quedó.

### S8 — No se rompe el contrato ni la suite

**Hecho cuando:** los 51 tests actuales de `inventory-service` (41 unit/slice + 10 IT) siguen verdes sin
modificarlos, el reactor sigue en verde y `spotless:check` pasa.

---

## Tasks

| ID | Descripción | Archivo |
|----|---|---|
| A | `MdcKeys` + `InstanceIdentity` + `CorrelationIdFilter` (mismo patrón que `order-service`) | `observability/*.java` |
| B | El filtro pone `X-Instance-Id` y `X-Correlation-Id`; el controller deja de poner la cabecera a mano | `observability/CorrelationIdFilter.java`, `controller/InventoryController.java` |
| C | Log estructurado `stock.reserve` con `result=OK\|INSUFFICIENT` | `service/InventoryReservationService.java` |
| D | `spring.jpa.open-in-view: false` + `logging.pattern.level` con `cid`/`inst` | `application.yml` |
| E | `idx_stock_movement_product_code` en DDL de producción, script de migración y esquema de test | `docker/postgres/*.sql`, `src/test/resources/inventory-schema.sql` |
| F | Test de contención: stock 5, 20 hilos → 5 éxitos, 15 rechazos, 5 movimientos | `it/InventoryReservationIT.java` |
| G | Tests de filtro, de log estructurado y de existencia del índice | `controller/`, `service/`, `it/` |
| H | Trazabilidad: `PLAN_IMPLEMENTACION.md` + `TAREAS_PENDIENTES.md` | `docs/plan/*.md` |
| I | Verificación: `mvn verify` (con Docker) + reactor + `spotless:check` + batería de mutaciones | — |

---

## Definition of Done

- [x] `X-Instance-Id` lo pone un filtro, no cada método del controller
- [x] El filtro devuelve `X-Correlation-Id` y lo deja en el MDC; el MDC se limpia al terminar
- [x] `logging.pattern.level` incluye `cid` e `inst`
- [x] Log `stock.reserve code=… qty=… order=… result=OK|INSUFFICIENT remaining=…`
- [x] `spring.jpa.open-in-view: false` y sin warning de Hibernate en el arranque
- [x] `idx_stock_movement_product_code` en producción, migración y esquema de test
- [x] IT de contención: stock 5 → 5 éxitos, 15 rechazos, stock 0, 5 movimientos
- [x] IT que verifica la existencia del índice
- [x] Tests del filtro (cabeceras + MDC) y del log estructurado
- [x] `PLAN_IMPLEMENTACION.md` y `TAREAS_PENDIENTES.md` con la trazabilidad de INV-01…INV-06 al día
- [x] `mvn -pl inventory-service verify` pasa (52 unit/slice + 14 IT con Postgres real, 0 omitidos)
- [x] `mvn verify` del reactor pasa
- [x] `mvn -pl inventory-service spotless:check` pasa
- [x] Batería de mutaciones: cada comportamiento nuevo está fijado por un test que lo detecta (10/10)

---

## Notas de implementación

- **Quitar la cabecera del controller es seguro**: ningún test la assertaba (verificado), y el filtro
  aplica a todas las rutas, incluidas las que no existen (404) y actuator.
- **`open-in-view: false`**: el único punto que serializa una entidad JPA fuera de una transacción es
  `GET /api/inventory/{productCode}`. La entidad no tiene relaciones lazy, así que la serialización no
  necesita sesión abierta. El IT de Postgres sube la app real con el flag activo, así que cualquier
  `LazyInitializationException` aparecería en la suite.
- **El log estructurado va en el servicio**, no en el controller: el servicio es el que sabe el resultado
  de negocio (`OK` / `INSUFFICIENT`) y el `remaining` real tras la reserva.
- **El test de contención** necesita 20 `orderNumber` distintos: con uno solo la idempotencia colapsa las
  20 peticiones en un movimiento.

---

## Comandos de verificación

```bash
cd src
JAVA_HOME=/usr/lib/jvm/java-21-openjdk mvn -pl inventory-service verify
JAVA_HOME=/usr/lib/jvm/java-21-openjdk mvn verify
JAVA_HOME=/usr/lib/jvm/java-21-openjdk mvn -pl inventory-service spotless:check
```

---

## Log

**L1:** 2026-11-24 13:35 — B4-13 creado. Cierra los 5 ítems del checklist de B4 en
`docs/plan/PLAN_IMPLEMENTACION.md` que nunca llegaron a ODD: filtros de observabilidad, log estructurado de
reserva, `open-in-view: false`, índice `idx_stock_movement_product_code` y el test de contención con stock
escaso. Suma la actualización de la trazabilidad (INV-01…INV-06) en el plan y en las tareas pendientes.

**L2:** 2026-11-24 13:50 — Implementado y verificado. **PASS.**

- Tareas A–I cerradas. `InventoryController` ya no pone `X-Instance-Id` a mano; lo pone
  `observability/CorrelationIdFilter` (junto con `X-Correlation-Id`), que además deja el MDC y lo limpia en
  el `finally`. `observability/{MdcKeys,InstanceIdentity,CorrelationIdFilter}.java` replican el patrón de
  `order-service`.
- Log estructurado en `InventoryReservationService`: una línea `INFO` por resultado de reserva
  (`result=OK` / `result=INSUFFICIENT`), sin traza.
- `application.yml`: `spring.jpa.open-in-view: false` y `logging.pattern.level` con `cid`/`inst`. El arranque
  ya no emite el warning de Hibernate; el IT que hace `GET /api/inventory/{code}` prueba que la
  serialización sigue funcionando sin sesión abierta.
- `idx_stock_movement_product_code` en `docker/postgres/02-init-schemas.sql`,
  `docker/postgres/migrate-stock-movement-index.sql` y `src/inventory-service/src/test/resources/inventory-schema.sql`.
  (Coincide con lo que ya documentaba `docs/database/DATABASE_SCHEMA.md`.)
- IT nuevos: contención con stock 5 (20 hilos → 5 éxitos, 15 rechazos, stock 0, reservado 5, 5 movimientos),
  existencia del índice, `open-in-view` desactivado y serialización del GET.
- Tests nuevos: `observability/CorrelationIdFilterTest` (4), `service/InventoryReservationLoggingTest` (4),
  3 tests de cabeceras en `InventoryControllerTest`.
- Trazabilidad: `PLAN_IMPLEMENTACION.md` (checklist de B4, Tests, tabla de ramas con ✅, INV-01…INV-06 y el
  nombre obsoleto `uq_stock_movement_ref_type` corregido a `uq_stock_movement_order_product_type`) y
  `TAREAS_PENDIENTES.md` (INV-01…INV-06 con estado y evidencia).
- Verificación: `mvn -pl inventory-service verify` → 52 unit/slice + 14 IT (Postgres 15 real), 0 fallos, 0
  omitidos. `mvn verify` del reactor → 142 tests en verde. `spotless:check` pasa.
- Batería de mutaciones: **10/10 mueren** (M1–M10), cada una por aserción, no por error de compilación.
- Desviación menor: `@Import(InstanceIdentity.class)` se añadió a `InventoryControllerTest` (el patrón de
  `order-service`) porque el `@WebMvcTest` carga el filtro y necesita su bean. No se modificó ninguna
  aserción de los 22 tests previos; los 22 siguen intactos y en verde.
