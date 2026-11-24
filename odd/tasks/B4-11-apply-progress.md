# Apply Progress — B4-11: Garantía de idempotencia real + test del guard de `release`

Rama: `brydyan/feature/inventory-reserve-hardening`
Base: `bd9cc0a`
ODD: `odd/tasks/B4-11-inventory-div-idempotence-guard.md`
Origen: WARN de la verificación `03-verify-odd` de B4-08 (2026-11-24 11:05)

## Tasks

- [ ] A — Test de slice JPA: `releaseAtomically` con `reserved_stock` insuficiente → 0 filas, fila intacta
- [ ] B — Test que fija la serialización por lock de fila (2 reservas concurrentes del mismo `(order, product)` → 1 movimiento)
- [ ] C — Aplicar la opción elegida en S2 (A: `ON CONFLICT DO NOTHING` / transacción nueva; B: lock como garantía principal)
- [ ] D — Documentar en el servicio la garantía real (lock de fila principal, índice único como red de seguridad)
- [ ] E — Reflejar la decisión en el ODD de B4-08 y en `API_CONTRACT.md`
- [ ] F — Verificación: `mvn -pl inventory-service verify` + `spotless:check` + batería de mutaciones

## Definition of Done

- [ ] Test de slice JPA: `releaseAtomically` con stock reservado insuficiente → 0 filas y fila intacta
- [ ] Ese test **falla** si se quita el guard `p.reservedStock >= :qty` (mutante M1 muere)
- [ ] Test que fija la serialización por lock de fila (mutante M10 muere)
- [ ] Decisión de S2 (A o B) aplicada en el código y documentada en el servicio
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
- La opción A de S2 usa `ON CONFLICT DO NOTHING`, que es SQL nativo de Postgres: su test tendría que vivir en el
  IT de Postgres (no en el slice H2). Si se elige la opción B, todo queda cubierta sin Docker.

## Commits

```
(ninguno todavía)
```
