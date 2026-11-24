# Apply Progress — B4-11: Garantía de idempotencia real + test del guard de `release`

Rama: `brydyan/feature/inventory-reserve-hardening`
Base: `bd9cc0a`
ODD: `odd/tasks/B4-11-inventory-div-idempotence-guard.md`
Origen: WARN de la verificación `03-verify-odd` de B4-08 (2026-11-24 11:05)
Coordinado con: `odd/tasks/B4-12-inventory-postgres-integration-tests-ci.md` (los ITs con Postgres)
Decisión del humano (2026-11-24): **opción A** — el 200 idempotente debe ser real, vía `ON CONFLICT DO NOTHING`

## Tasks

- [ ] A — Test de slice JPA: `releaseAtomically` con `reserved_stock` insuficiente → 0 filas, fila intacta
- [ ] B — Test que fija la serialización por lock de fila (2 reservas concurrentes del mismo `(order, product)` → 1 movimiento)
- [ ] C — Método de inserción con `ON CONFLICT (reference_order_number, movement_type, product_code) WHERE reference_order_number IS NOT NULL DO NOTHING` (opción A)
- [ ] C2 — **Reordenar** `reserveStock` / `releaseStock`: insertar el movimiento **antes** del UPDATE de stock, y en caso de conflicto re-leer el ganador y responder 200 **sin tocar el stock**
- [ ] C3 — IT de Postgres del camino de conflicto (necesita **B4-12** para correr en CI)
- [ ] D — Documentar en el servicio la garantía real (lock de fila principal, índice único como red de seguridad)
- [ ] E — Reflejar la decisión en el ODD de B4-08 y en `API_CONTRACT.md`
- [ ] F — Verificación: `mvn -pl inventory-service verify` + `spotless:check` + batería de mutaciones

## Definition of Done

- [ ] Test de slice JPA: `releaseAtomically` con stock reservado insuficiente → 0 filas y fila intacta
- [ ] Ese test **falla** si se quita el guard `p.reservedStock >= :qty` (mutante M1 muere)
- [ ] Test que fija la serialización por lock de fila (mutante M10 muere)
- [ ] Opción A aplicada: insert con `ON CONFLICT DO NOTHING` (opción A, decidida por el humano)
- [ ] El movimiento se inserta **antes** del UPDATE de stock; en conflicto **no** se toca el stock
- [ ] Conflicto → 200 con el movimiento ganador, sin excepción y sin transacción *rollback-only*
- [ ] La cadena de garantías (lock → `ON CONFLICT` → índice único) está escrita en el servicio
- [ ] El invariante de la idempotencia (lock + índice único) está escrito en el código y en el ODD de B4-08
- [ ] `API_CONTRACT.md` refleja la respuesta actual de `/release`
- [ ] Los 32 tests actuales de `inventory-service` siguen verdes
- [ ] `mvn -pl inventory-service verify` pasa
- [ ] `mvn -pl inventory-service spotless:check` pasa
- [ ] Batería de mutaciones repetida: M1, M10 y M7 quedan cubiertos

## Tests

| Alcance | Antes | Después |
|---|---:|---:|
| `inventory-service` (unit) | 32 | 34+ (0 failures, 0 errors, 0 skipped) |
| Reactor completo (JDK 21) | 102 | 104+ (0 failures, 0 errors, 0 skipped) |
| `inventory-service` IT con Docker | 4 skipped | 4 skipped (sin daemon en este entorno) |

## Desviaciones / TODO / gaps

- Docker no disponible: los 4 ITs de `InventoryReservationIT` quedan omitidos. H-3 (ejecutarlos con Postgres real)
  sigue abierto y **no** se cierra con esta ODD: es una tarea de entorno, no de código.
- El mutante M7 (`flushAutomatically` de `reserveAtomically`) sobrevive: es un gap menor, pero está listado en el
  DoD para que la batería de mutaciones quede completa.
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
(ninguno todavía)
```
