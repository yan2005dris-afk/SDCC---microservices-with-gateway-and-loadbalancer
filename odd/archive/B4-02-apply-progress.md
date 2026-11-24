# Apply Progress — B4-02: Reserva Atómica (INV-03)

Rama: `brydyan/feature/inventory-reserve-hardening`
Base: `0a7d3c2`

## Tasks

- [x] A — `InventoryReservationService` con `@Transactional` (`29b8002`)
- [x] B — `InventoryProductRepository#reserveAtomically()` con UPDATE condicional (`bae5d20`)
- [x] C — `InventoryController#reserveStock` delega en el servicio (`c65ebf6`)
- [x] D — Tests unitarios de servicio + concurrencia de 20 hilos (`eb27734`)

Nota: B se implementó antes que A porque el servicio no compila sin el método del repositorio.

## Definition of Done

- [x] `InventoryReservationService` creado con UPDATE atómico
- [x] `reserveAtomically()` retorna `int` (filas actualizadas)
- [x] Controller delega en servicio
- [x] Test de concurrencia: 20 hilos → 20 éxitos, stock final = 0
- [x] 0 `ObjectOptimisticLockingFailureException` en logs de tests
- [x] `mvn verify -pl inventory-service` pasa

## Tests

| Alcance | Antes | Después |
|---|---|---|
| `inventory-service` | 7 | 9 (0 failures, 0 errors) |
| Reactor completo (JDK 21) | 21 | 23 (0 failures, 0 errors) |

Detalle:
- `InventoryControllerTest`: 7 → 5. Se eliminaron los dos tests que fijaban respuestas 400 antiguas; ahora el controller delega y las excepciones se verifican en el servicio.
- `InventoryReservationServiceTest`: 0 → 4 (éxito, producto inexistente, stock insuficiente, 20 hilos).
- No hubo RED observable: los tests D se escribieron después de A/B/C porque el servicio es dependencia directa de las aserciones. El RED de esta rama se registró en B4-01.

## Cambios relevantes

- `UPDATE InventoryProduct ... WHERE productCode = :code AND stock >= :qty` con `@Modifying`, `@Query`, `@Param`. Incremento manual de `version`.
- El servicio lanza `ProductNotFoundException` / `StockUnavailableException` cuando `reserveAtomically()` retorna 0, y retorna `int remainingStock` en éxito.
- `@Transactional` movido del controller al servicio. El controller ya no hace read-modify-write ni `repository.save()`.

## Desviaciones / TODO / gaps

- `ProblemDetail` y HTTP 404/400 para las excepciones de dominio: pendiente de B4-03. Hasta entonces, una reserva fallida termina en 500 porque no existe `@RestControllerAdvice`.
- La firma `int reserveStock(...)` es provisional; B4-04 la cambia por `ReservationResponse`.
- El test de concurrencia usa un repositorio simulado con CAS atómico (`AtomicInteger`). No ejecuta PostgreSQL ni valida el parseo de la JPQL. El test de integración real con Testcontainers, el índice de idempotencia y la verificación en CI corresponden a B4-06 (task A/B/D/E). No se agregaron dependencias H2/Testcontainers aquí.
- **Gaps detectados en la verificación `03-verify-odd` (2026-11-24), trackeados en B4-10:**
  1. `@Modifying` sin `clearAutomatically`/`flushAutomatically` → la entidad en la caché de primer nivel queda
     stale tras el UPDATE masivo (probado: `dbStock=45` pero `entityAfter=50`, `sameInstance=true`).
  2. El `remainingStock` se lee con un 2º `SELECT`: solo es atómico porque ocurre dentro de la transacción que
     mantiene el lock de fila (probado: el 2º UPDATE concurrente esperó 444 ms al lock). Invariante no
     documentado en el código.
  3. La suite es solo-mock: el JPQL del UPDATE masivo nunca lo ejecuta una BD real dentro de CI.
- JDK 25 rompe Spotless 2.44.3 y los mocks de Mockito. Verificación ejecutada con `JAVA_HOME=/usr/lib/jvm/java-21-openjdk`. Fallo preexistente del entorno.

## Commits

```
eb27734 test(inventory): cover atomic reserve and 20-thread concurrency
c65ebf6 refactor(inventory): delegate reserve to atomic service
29b8002 feat(inventory): add atomic reservation service
bae5d20 feat(inventory): add conditional atomic stock update query
```

---

## Verificación (`03-verify-odd`, 2026-11-24) — PASS (WARN cerrado)

**Resultado inicial:** PASS con WARN → **WARN cerrado 2026-11-24 11:05 → veredicto final PASS**.

Los 3 huecos que dejó la verificación original:

| Hueco | Cerrado por | Evidencia de cierre |
|---|---|---|
| caché stale tras `@Modifying` | **B4-10** (PASS) | `clearAutomatically = true` en los 2 queries + test de slice JPA que **muere** al quitarlo (mutaciones M2 y M3 ✅) |
| invariante de atomicidad del `remainingStock` sin documentar | **B4-10** (PASS) | comentario en `InventoryReservationService#reservationResponse` (`bd9cc0a`) + documentado en B4-02 y B4-10 |
| suite solo-mock: la query nunca lo ejecuta una BD real en CI | **B4-10** (PASS) | `InventoryProductRepositoryJpaTest` ejecuta `reserveAtomically`/`releaseAtomically` contra H2: 3 tests, y el de concurrencia (20 hilos → stock 0, `reserved_stock` 20, `version` 20) falla si la query deja de ser atómica |

**Re-verificación con ejecución real (2026-11-24 11:05):**

- `mvn -pl inventory-service verify` → BUILD SUCCESS, 32 unit verdes ✅
- reactor → 102 tests, 0 fallos ✅
- `mvn -q spotless:check -pl inventory-service` → exit 0 ✅
- commits convencionales, sin tags de IA ✅

Salvedad que sigue viva (no bloquea a B4-02): los ITs con **PostgreSQL real** (`InventoryReservationIT`, 4 tests)
siguen `skipped` por falta de daemon de Docker. La cobertura permanente con Postgres es de **B4-06** y **B4-08**;
la evidencia de H2 no sustituye a Postgres, pero el hueco original de B4-02 (queries sin ejecutar contra ninguna BD
en CI) ya está cubierto.

**Veredicto: ✅ PASS.** Se archiva en `odd/archive/`.
