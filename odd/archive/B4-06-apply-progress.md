# Apply Progress — B4-06: Tests de Integración (INV-01)

Rama: `brydyan/feature/inventory-reserve-hardening`
ODD: `odd/archive/B4-06-inventory-tests.md`
Estado: **PASS** (verificado el 2026-11-24 13:30 con Postgres 15 real)

> B4-06 se implementó en dos tiempos: quedó pausada hasta que B4-07 y B4-08 cerraran sus reworks; con ambas
> en PASS se completaron los escenarios de integración full-stack que faltaban.

## Tasks

- [x] A — Dependencias Testcontainers + failsafe en `pom.xml`
- [x] B — `InventoryControllerTest` (@WebMvcTest) — hoy 22 casos
- [x] C — Tests unitarios: validación, 404, stock insuficiente
- [x] D — `InventoryReservationIT` (@SpringBootTest + Testcontainers) — hoy **10 casos**
- [x] E — Test de concurrencia: 20 threads → 20 éxitos, stock = 0
- [x] F — Tests de idempotencia: `/reserve` y `/release`
- [x] G — Test de flujo completo: `reserve → release` con los 2 movimientos
- [x] H — CI workflow con `mvn verify` (B4-12)

## Definition of Done

- [x] `pom.xml` incluye testcontainers + failsafe
- [x] `InventoryControllerTest` con 5 casos unitarios (hoy 22)
- [x] `InventoryReservationIT` con 5+ casos de integración (hoy **10**)
- [x] Tests de concurrencia: 20 threads → 0 conflictos
- [x] Tests de idempotencia: reserve y release
- [x] CI workflow actualizado: `mvn verify`
- [x] `mvn verify -pl inventory-service` pasa en local
- [x] `mvn verify -pl inventory-service` pasa en CI (el paso de CI ejecuta el comando verificado en local)

## Escenarios del spec S3 cubiertos

| Escenario del spec | Test | Estado |
|---|---|---|
| Concurrencia: 20 threads, stock 20 → 0 | `twentyConcurrentReservesOfDistinctOrdersDrainStockToZero` | ✔ nuevo |
| Idempotencia de reserva | `sameOrderReservesEachProductOnceAndIsIdempotentPerProduct` | ✔ previo |
| `/release` idempotente (2ª llamada → 200, stock no cambia) | `releaseIsIdempotentAndDoesNotRestoreStockTwice` | ✔ nuevo |
| Flujo completo `reserve → release` + 2 movimientos | `fullReserveReleaseFlowLeavesReservationAndReleaseMovements` | ✔ nuevo |
| Producto inexistente → 404 | `reserveUnknownProductReturns404ProblemDetail` | ✔ nuevo |
| Stock insuficiente → 400 `available:false` + `remainingStock` | `reserveWithoutEnoughStockReturns400WithRemainingStockAndNoMovement` | ✔ nuevo |

Los 5 ITs previos (idempotencia por `(order, product)`, 20 hilos con el mismo order, 409 por liberar de más,
camino de conflicto `ON CONFLICT`, esquema con `CHECK` e índice único) se conservaron sin cambios.

## Tests

| Alcance | Antes | Después |
|---|---:|---:|
| `InventoryControllerTest` | 22 | 22 (0 fallos) |
| `InventoryReservationServiceTest` | 13 | 13 (0 fallos) |
| `InventoryProductRepositoryJpaTest` (H2) | 6 | 6 (0 fallos) |
| `InventoryReservationIT` (Postgres 15 real) | 5 | **10** (0 fallos, 0 omitidos) |

## Batería de mutaciones sobre los tests nuevos

Un test que no detecta la mutación no fija nada. Los 5 ITs nuevos se sometieron a mutaciones dirigidas:

| Mutación | Test que la detecta | Resultado |
|---|---|---|
| N1 — `/release` pierde el corto-circuito de idempotencia | `releaseIsIdempotentAndDoesNotRestoreStockTwice` | 💀 murió |
| N2 — el lock de producto inexistente devuelve 400 en vez de 404 | `reserveUnknownProductReturns404ProblemDetail` | 💀 murió |
| N3 — se quita el guard `p.stock >= :qty` | `reserveWithoutEnoughStockReturns400WithRemainingStockAndNoMovement` | 💀 murió |
| N4 — el movimiento de RELEASE no se inserta | `fullReserveReleaseFlowLeavesReservationAndReleaseMovements` (+ `releaseIsIdempotent…`) | 💀 murió |

> Nota de método: la primera pasada de N2 **sobrevivió** porque la mutación apuntaba a la rama `updated == 0`,
> que en este flujo nunca se alcanza (el lock falla antes). Corregida la mutación para que ejercite la rama
> real, el test la detecta.

## Desviaciones / TODO / gaps

- El escenario de concurrencia del spec usa **20 órdenes distintas** (`ORD-DRAIN-00..19`). Con un solo
  `orderNumber` la idempotencia colapsaría las 20 peticiones en un solo movimiento y el stock no llegaría a 0;
  el spec exige "stock final = 0", que solo es alcanzable con peticiones distinctes.
- El ítem "`mvn verify` pasa **en CI**" se verificó en su equivalente local (mismo comando, mismo JDK 21,
  Docker disponible). La corrida remota queda confirmada por el pipeline en el próximo push.
  **→ Confirmado en remoto el 2026-11-24 14:31:** PR #10, workflow `CI - Inventory Service`, paso
  `mvn -f src/pom.xml verify -pl inventory-service` en **success** (los ITs con Postgres 15 real corrieron
  en el runner, 0 omitidos). Ver `odd/verification/B4-verification-2026-11-24.md` §9.
- B4-06 no bloquea a ninguna otra ODD: su infraestructura (contenedor, failsafe, CI) ya estaba en pie desde
  B4-12.

## Commits

```
(pendiente)
```
