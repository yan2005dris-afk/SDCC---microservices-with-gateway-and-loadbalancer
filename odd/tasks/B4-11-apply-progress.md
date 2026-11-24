# Apply Progress — B4-11: Garantía de idempotencia real + test del guard de `release`

Rama: `brydyan/feature/inventory-reserve-hardening`
Base: `bd9cc0a`
ODD: `odd/tasks/B4-11-inventory-div-idempotence-guard.md`
Origen: WARN de la verificación `03-verify-odd` de B4-08 (2026-11-24 11:05)
Coordinado con: `odd/tasks/B4-12-inventory-postgres-integration-tests-ci.md` (los ITs con Postgres)
Decisión del humano (2026-11-24): **opción A** — el 200 idempotente debe ser real, vía `ON CONFLICT DO NOTHING`

## Tasks

- [x] A — Test de slice JPA: `releaseAtomically` con `reserved_stock` insuficiente → 0 filas, fila intacta
- [x] B — Test que fija la serialización por lock de fila (2 reservas concurrentes del mismo `(order, product)` → 1 movimiento)
- [x] C — Método de inserción con `ON CONFLICT (reference_order_number, movement_type, product_code) WHERE reference_order_number IS NOT NULL DO NOTHING` (opción A)
- [x] C2 — **Reordenar** `reserveStock` / `releaseStock`: insertar el movimiento **antes** del UPDATE de stock, y en caso de conflicto re-leer el ganador y responder 200 **sin tocar el stock**
- [x] C3 — IT de Postgres del camino de conflicto (necesita **B4-12** para correr en CI)
- [x] D — Documentar en el servicio la garantía real (lock de fila principal, índice único como red de seguridad)
- [x] E — Reflejar la decisión en el ODD de B4-08 y en `API_CONTRACT.md`
- [x] F — Verificación: `mvn -pl inventory-service verify` + `spotless:check` + batería de mutaciones

## Definition of Done

- [x] Test de slice JPA: `releaseAtomically` con stock reservado insuficiente → 0 filas y fila intacta
- [x] Ese test **falla** si se quita el guard `p.reservedStock >= :qty` (mutante M1 muere)
- [x] Test que fija la serialización por lock de fila (mutante M10 muere)
- [x] Test que fija el `flushAutomatically` del bulk update (mutante M7 muere)
- [x] Opción A aplicada: insert con `ON CONFLICT DO NOTHING` (opción A, decidida por el humano)
- [x] El movimiento se inserta **antes** del UPDATE de stock; en conflicto **no** se toca el stock
- [x] Conflicto → 200 con el movimiento ganador, sin excepción y sin transacción *rollback-only*
- [x] La cadena de garantías (lock → `ON CONFLICT` → índice único) está escrita en el servicio
- [x] El invariante de la idempotencia (lock + índice único) está escrito en el código y en el ODD de B4-08
- [x] `API_CONTRACT.md` refleja la respuesta actual de `/release`
- [x] Los 36 tests actuales de `inventory-service` siguen verdes
- [x] `mvn -pl inventory-service verify` pasa
- [x] `mvn -pl inventory-service spotless:check` pasa
- [x] Batería de mutaciones repetida: M1, M10 y M7 quedan cubiertos

## Tests

| Alcance | Antes | Después |
|---|---:|---:|
| `inventory-service` (unit) | 32 | 36 (0 failures, 0 errors, 0 skipped) |
| `inventory-service` IT con Docker | 4 skipped | 5 ejecutados (0 failures, 0 errors, 0 skipped) |
| Reactor completo (JDK 21) | 102 | 106 (0 failures, 0 errors, 0 skipped) |

## Desviaciones / TODO / gaps

- Docker/Postgres disponible: los ITs de `InventoryReservationIT`, incluido el nuevo test de conflicto, corren con
  Postgres real. La omisión silenciosa quedó eliminada en B4-12.
- Batería de mutaciones completada: M1 (guard de `releaseAtomically`) muere con
  `releaseBelowReservedStockUpdatesNoRows`; M10 (`@Lock(PESSIMISTIC_WRITE)`) muere con
  `forUpdateLookupUsesPessimisticWriteLock`; M7 (`flushAutomatically`) muere con
  `reserveBulkUpdateFlushesBeforeExecuting`. Los tres mutantes fueron restaurados.
- La opción A de S2 usa `ON CONFLICT DO NOTHING`, que es SQL nativo de Postgres: su test **tiene** que vivir en el
  IT de Postgres (no en el slice H2). B4-12 es lo que habilita esa verificación (ITs en CI + Postgres real).
- `ON CONFLICT (cols) WHERE predicado DO NOTHING` exige que el objetivo de conflicto coincida con el índice **y su
  predicado**: el índice es parcial (`WHERE reference_order_number IS NOT NULL`), así que el `WHERE` del
  `ON CONFLICT` es obligatorio. Si no coincide, Postgres **rechaza** la sentencia → a probar en el IT.
- Trampa de diseño evitada por el reordenamiento: si el UPDATE de stock fuera **antes** del insert y este
  resolviera el conflicto sin excepción, la transacción no se marcaría *rollback-only* → el commit **conservaría el
  descuento** → dos peticiones idénticas descontarían dos veces (sobreventa silenciosa). Por eso el movimiento va
  primero.

## Commits

```
66c8965 test(inventory): pin release stock guard in jpa slice
28cc5d2 feat(inventory): make stock idempotency conflict-safe
f72f540 test(inventory): cover postgres conflict-safe insert
87470dc test(inventory): pin idempotency guard invariants
```


## Re-verificación independiente (2026-11-24 12:20)

Ejecutada de nuevo, con Docker **disponible**, sobre el árbol que incluye B4-07:

```bash
cd src
JAVA_HOME=/usr/lib/jvm/java-21-openjdk mvn -pl inventory-service verify
```

- **BUILD SUCCESS** — 41 unit/slice (22 controller + 13 service + 6 JPA slice) + **5 ITs contra Postgres 15 real**
  (`InventoryReservationIT`), 0 failures, 0 errors, **0 skipped**.
- El camino de conflicto `ON CONFLICT DO NOTHING` queda validado con Postgres real: el insert duplicado devuelve
  `0` filas, la transacción **no** queda *rollback-only* y la reserva repetida sigue en `200` con el stock intacto.
- Reactor completo: **111 tests** (gateway 7 · order 63 · inventory 41), 0 failures.
- `mvn -pl inventory-service spotless:check` en verde.

La opción A queda **verificada en ejecución**, no solo por lectura de código.
