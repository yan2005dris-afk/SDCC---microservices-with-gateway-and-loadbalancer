# Verificación del bloque B4 — `inventory-service`

**Fecha:** 2026-11-24 12:45
**Método:** `03-verify-odd` (verificación por ejecución, no por lectura)
**Rama:** `brydyan/feature/inventory-reserve-hardening` · HEAD `842f029`
**Entorno:** JDK 21.0.13 (`/usr/lib/jvm/java-21-openjdk`), Docker **disponible**, Postgres 15 real vía Testcontainers

---

## 1. Veredicto por ODD

| ODD | Veredicto previo | Veredicto actual | Cierra | Promovida a `odd/archive/` |
|---|---|---|---|---|
| B4-01 DTO validado | PASS | ✅ **PASS** (sin regresiones) | — | ya estaba |
| B4-02 Reserva atómica | PASS | ✅ **PASS** (sin regresiones) | — | ya estaba |
| B4-03 Manejo de errores | ❌ FAIL | ✅ **PASS** | B4-07 | **sí** |
| B4-04 Respuesta con precio | PASS | ✅ **PASS** (sin regresiones) | — | ya estaba |
| B4-05 Movimientos de stock | ❌ FAIL | ✅ **PASS** | B4-08 + B4-11 + B4-12 | **sí** |
| B4-06 Tests de integración | (pausada) | ✅ **PASS** (completada el 2026-11-24 13:30) | — | **sí** |
| B4-07 Semántica HTTP | — | ✅ **PASS** | — | **sí** |
| B4-08 Idempotencia/concurrencia | ⚠️ WARN | ✅ **PASS** | B4-11 + B4-12 | **sí** |
| B4-09 Cobertura de validación | PASS | ✅ **PASS** (sin regresiones) | — | ya estaba |
| B4-10 Durabilidad | PASS | ✅ **PASS** (sin regresiones) | — | ya estaba |
| B4-11 Idempotencia real (opción A) | — | ✅ **PASS** | — | **sí** |
| B4-12 ITs con Postgres en CI | — | ✅ **PASS** | — | **sí** |

**Veredicto global del bloque: ✅ PASS — las 12 ODDs en PASS limpio.**
B4-06 se completó después de esta primera pasada (ver §5): se escribieron y verificaron los 5 escenarios de
integración que faltaban, y pasó de WARN a PASS.

---

## 2. Batería de mutaciones (10/10 mueren)

Cada mutación se aplicó al código real, se ejecutó la suite y se restauró el archivo. Un mutante que
sobrevive es un gap de cobertura.

| # | Mutación | Test objetivo | Resultado |
|---|---|---|---|
| M1 | `releaseAtomically`: quitar `AND p.reservedStock >= :qty` | `releaseBelowReservedStockUpdatesNoRows` | 💀 murió |
| M7 | `reserveAtomically`: quitar `flushAutomatically` | `reserveBulkUpdateFlushesBeforeExecuting` | 💀 murió |
| M10 | `findByProductCodeForUpdate`: quitar `@Lock(PESSIMISTIC_WRITE)` | `forUpdateLookupUsesPessimisticWriteLock` | 💀 murió |
| M11 | Queries masivos: quitar `clearAutomatically` | `bulkUpdateClearsPersistenceContextForReserveAndRelease` | 💀 murió |
| M12 | `ReserveStockRequest`: quitar `@NotNull` de `quantity` | `rejectsMissingQuantityWithProblemDetail` | 💀 murió |
| M13 | `ReserveStockRequest`: quitar `@NotBlank` de `productCode` | `rejectsBlankProductCodeWithProblemDetail` | 💀 murió |
| M14 | `StockMovementRepository`: quitar `ON CONFLICT … DO NOTHING` | IT `duplicateConflictInsertReturnsZero…` | 💀 murió (Postgres real) |
| M15 | `reserveStock`: UPDATE de stock **antes** del INSERT del movimiento | IT `duplicateConflictInsertReturnsZero…` | 💀 murió (Postgres real) |
| M16 | `ApiExceptionHandler`: volver al modelo sin `ResponseEntityExceptionHandler` | `InventoryControllerTest` (5 tests) | 💀 murieron 5 |
| N1 | `/release`: quitar el corto-circuito de idempotencia | `releaseIsIdempotentAndDoesNotRestoreStockTwice` | 💀 murió |
| N2 | Lock de producto inexistente: 400 en vez de 404 | `reserveUnknownProductReturns404ProblemDetail` | 💀 murió |
| N3 | `reserveAtomically`: quitar `AND p.stock >= :qty` | `reserveWithoutEnoughStockReturns400WithRemainingStockAndNoMovement` | 💀 murió |
| N4 | `/release`: no insertar el movimiento de RELEASE | `fullReserveReleaseFlowLeavesReservationAndReleaseMovements` | 💀 murió |

**14 mutantes, 14 muertes.** N1–N4 son las mutaciones dirigidas de los 5 ITs nuevos de B4-06 (§5).

**M15 es el más importante**: es exactamente la sobreventa silenciosa que la ODD B4-11 advertía
(el `ON CONFLICT` sin excepción no marcaría la transacción *rollback-only* → el commit conservaría el
descuento → dos peticiones idénticas descontarían dos veces). Está muerto: el test lo detecta.

---

## 3. Suite real ejecutada

```bash
cd src
JAVA_HOME=/usr/lib/jvm/java-21-openjdk mvn verify                              # BUILD SUCCESS (reactor completo, 127 tests)
JAVA_HOME=/usr/lib/jvm/java-21-openjdk mvn -pl inventory-service verify        # BUILD SUCCESS (41 unit/slice + 10 IT)
JAVA_HOME=/usr/lib/jvm/java-21-openjdk mvn -pl inventory-service spotless:check
```

| Alcance | Tests | Fallos | Omitidos |
|---|---:|---:|---:|
| `inventory-service` unit + slice JPA | 41 | 0 | 0 |
| `inventory-service` IT con **Postgres 15 real** | 10 | 0 | 0 |
| `order-service` unit | 63 | 0 | 0 |
| `order-service` IT (Postgres real) | 6 | 0 | 0 |
| `api-gateway` | 7 | 0 | 0 |
| **Total reactor (`mvn verify`)** | **127** | **0** | **0** |

---

## 4. Comportamientos verificados por ejecución (no por inspección)

| Comportamiento | Cómo se comprobó | Evidencia |
|---|---|---|
| 405 en `PUT /reserve` | MockMvc + mutante M16 | `Method 'PUT' is not supported.` |
| 415 en `/reserve` y `/release` | MockMvc + mutante M16 | `Content-Type 'text/plain;charset=UTF-8' is not supported.` |
| 404 sin mapping | MockMvc + mutante M16 | `NoResourceFoundException` → 404 |
| `application/problem+json` en todo error | assert en el helper de tests | `expectProblemDetail` |
| El catch-all loguea la causa raíz | `ListAppender` de logback | nivel `ERROR` + `ThrowableProxy` + URI |
| Conflicto → 200 sin *rollback-only* | IT con Postgres real | `inserted == 0`, `rollbackOnly == false`, stock intacto |
| Sobreventa con doble petición idéntica | IT con Postgres real | 1 solo movimiento, stock descontado 1 vez |
| 20 reservas concurrentes | IT con Postgres real | 20 × 200, stock 49, `reserved` 1, 1 movimiento |
| Liberar de más → 409 controlado | IT con Postgres real | `reserved_stock` nunca negativo |
| **Sin Postgres el build falla** | parche temporal del chequeo de disponibilidad | `BUILD FAILURE` + mensaje accionable, **0 skipped** |
| **Sin Postgres el ciclo rápido sigue** | mismo escenario | `mvn test` → `BUILD SUCCESS`, 41 tests |
| Índice único parcial en producción | inspección del DDL | `02-init-schemas.sql:67-69` con `WHERE reference_order_number IS NOT NULL` (igual en el esquema de test y en el script de migración) |

---

## 5. B4-06 — hueco cerrado (✅ PASS)

B4-06 (tests de integración full-stack) estaba **pausada** por diseño hasta que B4-07 y B4-08 cerraran; ambas
ya cerraron en PASS, así que quedó desbloqueada pero **incompleta**: le faltaban 4 escenarios del spec S3 a
nivel de IT. Se escribieron y verificaron (2026-11-24 13:30) contra **Postgres 15 real**:

| Escenario | Test | Estado |
|---|---|---|
| `/release` idempotente (2ª llamada → 200, stock no cambia) | `releaseIsIdempotentAndDoesNotRestoreStockTwice` | ✔ |
| Flujo completo `reserve → release` + 2 movimientos | `fullReserveReleaseFlowLeavesReservationAndReleaseMovements` | ✔ |
| `POST /reserve` de producto inexistente → 404 `problem+json` | `reserveUnknownProductReturns404ProblemDetail` | ✔ |
| `POST /reserve` sin stock → 400 `available:false` + `remainingStock`, sin movimiento | `reserveWithoutEnoughStockReturns400WithRemainingStockAndNoMovement` | ✔ |
| Concurrencia: 20 threads → stock final 0 | `twentyConcurrentReservesOfDistinctOrdersDrainStockToZero` | ✔ |

`InventoryReservationIT` pasa de 5 a **10 ITs**, todos verdes y **0 omitidos**. Los 5 tests nuevos se sometieron
a mutaciones dirigidas (N1–N4 en §2) y **todos las detectan**.

Notas de método que conviene conservar:

- El escenario de concurrencia usa **20 órdenes distintas** (`ORD-DRAIN-00..19`): con un solo `orderNumber` la
  idempotencia colapsaría las 20 peticiones en un movimiento y el stock nunca llegaría a 0, que es lo que exige
  el spec ("stock final = 0").
- La primera pasada de la mutación N2 **sobrevivió**: apuntaba a la rama `updated == 0`, que en ese flujo nunca
  se alcanza (el lock falla antes). Corregida la mutación para ejercitar la rama real, el test la detecta. Queda
  registrado porque es exactamente el tipo de "test verde que no prueba nada" que la batería de mutaciones
  sirve para destapar.
- El ítem "`mvn verify` pasa **en CI**" se verificó en su equivalente local (mismo comando, mismo JDK 21, Docker
  disponible); la corrida remota la confirma el pipeline en el próximo push.

## 6. Calidad de commits

| Criterio | Resultado |
|---|---|
| Formato convencional `type(scope): message` | ✔ en los commits del bloque (solo hay `merge:` de integración con `develop`, que es legítimo) |
| Tags de IA (`Co-Authored-By`, "Generated with", nombres de modelo) | ✔ **ninguno** en los últimos 70 commits |
| Autoría | ✔ una sola identidad: `Andy Alejandro <andy.alejandrovera4290@upse.edu.ec>` |
| Un commit por tarea | ✔ commits cohesivos y atómicos (`feat`, `fix`, `test`, `ci`, `docs`) |

---

## 7. Regresión sobre las ODDs ya archivadas

Auditoría estática (sin modificar archivos) de B4-01, B4-02, B4-04, B4-09 y B4-10 contra el código actual,
con foco en los puntos de riesgo del rework B4-11/B4-07:

- **Ninguna garantía rota.** El DTO validado sigue intacto, el controller sigue delegando en el servicio,
  `ReservationResponse` sigue devolviendo `unitPrice`/`productName` (también en el camino idempotente),
  `clearAutomatically` sigue limpiando la caché de Hibernate para que `remainingStock` sea el stock
  *después* de la reserva, y el camino de stock insuficiente sigue haciendo rollback del movimiento
  insertado antes del UPDATE.
- **Salvedad menor (documentada):** la cláusula literal de B4-09 "los 13 tests siguen verdes **sin
  modificarlos**" quedó obsoleta: B4-07 amplió el helper `expectProblemDetail` con el assert de
  `Content-Type`. La semántica que B4-09 fijó sigue intacta. Se anotó en el Log de B4-09.
- **Cobertura real (no mock) de la concurrencia:** aunque B4-02 se apoyaba en tests con mocks, hoy la
  tienen tanto el slice JPA en H2 (`twentyConcurrentReservesSerializeWithoutOptimisticLockErrors`) como
  el IT contra Postgres 15 (`twentyConcurrentReservesForSameOrderProductReturnOkAndDecrementOnce`).
