# Apply Progress — B4-05: Movimientos de Stock + Endpoint /release (INV-06)

Rama: `brydyan/feature/inventory-reserve-hardening`
Base: `306920f`

## Tasks

- [x] A — `StockMovement` entity + `MovementType` enum (`6103937`)
- [x] B — `StockMovementRepository` (`41fae67`)
- [x] C — Índice único en `02-init-schemas.sql` (`a2edd99`)
- [x] D — `InventoryReservationService` inserta RESERVATION + idempotencia de reserve (`666761e`)
- [x] E — `ReleaseStockRequest` DTO (`aef58e5`)
- [x] F — `POST /api/inventory/release` (`f176411`)
- [x] G — Lógica idempotente de release en servicio (`f176411`)
- [x] H — Tests de idempotencia y release (`23e4410`)

## Definition of Done

- [x] `StockMovement` entity creada con `reference_order_number`, `movement_type`
- [x] Índice único en SQL aplicado
- [x] `InventoryReservationService` inserta `StockMovement(RESERVATION)` en la misma transacción
- [x] `POST /release` creado e idempotente
- [x] Dos `/reserve` del mismo `orderNumber` → segundo no decrementa (test unitario)
- [x] Dos `/release` del mismo `orderNumber` → segundo no restaura (test unitario)
- [x] Tests pasan
- [x] `mvn verify -pl inventory-service` pasa

## Tests

| Alcance | Antes | Después |
|---|---|---|
| `inventory-service` | 14 | 22 (0 failures, 0 errors) |
| Reactor completo (JDK 21) | 28 | 36 (0 failures, 0 errors) |

`InventoryControllerTest`: 10 → 13. Agregados: release 200, release con `orderNumber` vacío → 400 ProblemDetail, release sin reserva → 404 ProblemDetail.

`InventoryReservationServiceTest`: 4 → 9. Agregados: movimiento RESERVATION con orderNumber, reserve repetido sin segundo decremento, release con movimiento RELEASE y stock restaurado, release repetido sin segunda restauración, release sin reserva → excepción.

No hubo RED observable por cambio de firma: el cambio de `reserveStock` a 3 argumentos rompía compilación hasta propagarse a controller y mocks.

## Contrato

Reserve con `orderNumber`:

```json
{
  "productCode": "PROD-001",
  "available": true,
  "remainingStock": 45,
  "unitPrice": 1899.99,
  "productName": "Laptop Dell XPS 15",
  "message": "Reserva confirmada"
}
```

Release:

```json
{
  "productCode": "PROD-001",
  "quantity": 5,
  "message": "Stock liberado exitosamente"
}
```

Ambos mantienen el header `X-Instance-Id`.

## Desviaciones / TODO / gaps

- Tasks F y G comparten commit (`f176411`): el endpoint no compila sin el método de servicio. Separarlos habría dejado un commit intermedio en rojo.
- Se agregaron tres elementos no listados en la tabla de tasks pero necesarios para el contrato:
  - `ReservationNotFoundException` + handler 404 en `ApiExceptionHandler` — sin él el catch-all de B4-03 devolvía 500 para releases sin reserva.
  - `InventoryProductRepository.releaseAtomically(...)` — evita read-modify-write en release.
  - `InventoryReservationService.reserveStock` pasó de 2 a 3 argumentos para recibir `orderNumber` del DTO existente.
- Idempotencia probada en secuencia (mismo hilo). Dos peticiones concurrentes con el mismo `orderNumber` pueden pasar la comprobación previa; el índice único evita el doble movimiento, pero la segunda petición podría terminar en 500 por `DataIntegrityViolationException` en lugar de 200. B4-06 debe cubrir este caso con PostgreSQL real y, si falla, gestionarse en un task posterior.
- El índice único solo se aplica al inicializar una BD nueva con `02-init-schemas.sql`.Una BD ya creada necesita aplicar el índice manualmente o mediante migración.
- JDK 25 rompe Spotless 2.44.3 y Mockito. Verificación ejecutada con `JAVA_HOME=/usr/lib/jvm/java-21-openjdk`.

## Commits

```
23e4410 test(inventory): cover reserve and release idempotency
f176411 feat(inventory): add idempotent stock release endpoint
aef58e5 feat(inventory): add release stock request DTO
666761e feat(inventory): record RESERVATION movements with idempotent reserve
a2edd99 feat(db): add unique index for stock movement idempotency
41fae67 feat(inventory): add stock movement repository
6103937 feat(inventory): add stock movement model
```

---

## Verificación (2026-11-24 09:10) — FAIL

Re-verificado contra HEAD con probes reales (esquema replicado del DDL de Postgres 15:
`docker/postgres/02-init-schemas.sql`, con `CHECK (stock >= 0)`, `CHECK (reserved_stock >= 0)` y el índice
único `uq_stock_movement_ref_type`). Docker sigue caído → H2 con el mismo esquema.

| # | Hallazgo | Evidencia | Estado |
|---|---|---|---|
| 1 | La idempotencia se keyea **solo por `orderNumber`**, no por producto: la 2ª reserva de OTRO producto con el mismo `orderNumber` responde **200 sin reservar nada** | `P1 after reserve A: PROD-A=45/5 PROD-B=30/0` → 2ª reserva PROD-B: `available=true remainingStock=30` y `PROD-B` sigue `30/0` | ❌ FAIL |
| 2 | `releaseAtomically` **no tiene guard** `reservedStock >= :qty` | con CHECK de Postgres → `EXCEPTION DataIntegrityViolationException` (fila queda `45/0`, rollback) → **500** en vez de un 400 controlado | ❌ FAIL |
| 2b | El mismo release **sin** el CHECK (otra BD / si se cae el constraint) → corrupción silenciosa | `P2b updated=1 row(stock/reserved)=49/-4` → `reserved_stock = -4` y **stock inflado 45→49** (sobreventa) | ❌ FAIL (preexistente solo en el DDL de Postgres) |
| 3 | El check-then-insert del servicio no es atómico → 2 reservas concurrentes con el mismo `orderNumber` violan el índice único → **500** en vez de 200 | `P3 A check -> Optional.empty` → `A insert -> DataIntegrityViolationException (índice único violado)` | ❌ FAIL |

**Corrección a la caracterización previa:** el `reserved_stock` negativo **no** ocurre en Postgres gracias al
`CHECK (reserved_stock >= 0)` del DDL; allí el síntoma es un **500 por violación de constraint**. El
`reserved = -4` / `stock = 49` solo aparece si el constraint no está (o se cae). El defecto real es que
`releaseAtomically` **no valida el invariante en la propia query** y el error sale como 500 en vez de 400.

**Rework:** `odd/tasks/B4-08-inventory-idempotence-concurrency.md` — con errata obligatoria en
`odd/tasks/B4-08-verification-errata.md` (la S1 actual reproduce el defecto y la S4 parte de una premisa
falsa: el constraint `CHECK` **sí existe** en `02-init-schemas.sql`).

**Bonus (no bloqueante):** el índice único solo se crea al inicializar una BD **nueva**. Una BD ya inicializada
con el esquema viejo queda sin él → la idempotencia por `orderNumber` no está protegida en ese caso.

---

## Actualización (2026-11-24 11:05) — B4-08 cerrada en WARN, B4-05 sigue ABIERTA

Los tres hallazgos de este FAIL están **corregidos en el código** por el rework B4-08
(`odd/tasks/B4-08-inventory-idempotence-concurrency.md` + errata):

| Hallazgo original | Estado tras B4-08 | Evidencia |
|---|---|---|
| 1 — idempotencia solo por `orderNumber` (sobreventa silenciosa multi-línea) | ✅ **corregido** | la idempotencia es por `(orderNumber, productCode)`; probe P1: 2ª línea `PROD-B` reserva de verdad (`30→27`, `reserved=3`); mutante M4 muere |
| 2 — `releaseAtomically` sin guard `reservedStock >= :qty` | ✅ **corregido** (pero sin test) | el guard está en la query; probe P3/H2: liberar de más → **409** controlado, `reserved_stock` nunca negativo. **Gap:** el mutante M1 sobrevive (ver abajo) |
| 3 — check-then-insert no atómico → 500 en vez de 200 | ✅ **corregido en la práctica** | probe P2: 20 hilos → `ok=20 errores=0`, stock `49/1`, **1** movimiento. La serialización la da el lock `PESSIMISTIC_WRITE` sobre la fila de producto |

**B4-05 NO se archiva todavía.** Verificación de B4-08 = **WARN** con tres huecos abiertos:

- **H-1** — el catch de `DataIntegrityViolationException` promete un 200 idempotente que la transacción
  *rollback-only* no puede dar (el commit lanza `UnexpectedRollbackException` → 500). Hoy es inalcanzable por el
  lock de fila, pero el código dice una cosa y hace otra → **B4-11**.
- **H-2** — el guard del invariante de `release` no tiene ninguna prueba ejecutable que lo proteja (mutante M1
  sobrevive): solo lo cubre el IT de Postgres, que queda `skipped` sin Docker → **B4-11**.
- **H-3** — los 4 ITs de Postgres nunca se han ejecutado (daemon de Docker caído): el `FOR UPDATE` y el `CHECK`
  reales no están validados. → **B4-12** (ITs en CI con Testcontainers + el build falla, en vez de omitir, cuando no
  hay Postgres).

**Rework:** `odd/tasks/B4-11-inventory-div-idempotence-guard.md`.
**Cierre de B4-05:** cuando B4-11 cierre sus DoD y los ITs se hayan ejecutado en un entorno con Docker.
