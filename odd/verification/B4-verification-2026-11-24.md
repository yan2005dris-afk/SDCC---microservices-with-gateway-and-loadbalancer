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
| B4-06 Tests de integración | (pausada) | ⚠️ **WARN** | — | **no** (ver §5) |
| B4-07 Semántica HTTP | — | ✅ **PASS** | — | **sí** |
| B4-08 Idempotencia/concurrencia | ⚠️ WARN | ✅ **PASS** | B4-11 + B4-12 | **sí** |
| B4-09 Cobertura de validación | PASS | ✅ **PASS** (sin regresiones) | — | ya estaba |
| B4-10 Durabilidad | PASS | ✅ **PASS** (sin regresiones) | — | ya estaba |
| B4-11 Idempotencia real (opción A) | — | ✅ **PASS** | — | **sí** |
| B4-12 ITs con Postgres en CI | — | ✅ **PASS** | — | **sí** |

**Veredicto global del bloque: ✅ PASS con una ODD abierta (B4-06, WARN).**
10 de 12 ODDs en PASS limpio; 1 WARN con huecos concretos y ya identified.

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

**M15 es el más importante**: es exactamente la sobreventa silenciosa que la ODD B4-11 advertía
(el `ON CONFLICT` sin excepción no marcaría la transacción *rollback-only* → el commit conservaría el
descuento → dos peticiones idénticas descontarían dos veces). Está muerto: el test lo detecta.

---

## 3. Suite real ejecutada

```bash
cd src
JAVA_HOME=/usr/lib/jvm/java-21-openjdk mvn -pl inventory-service verify   # BUILD SUCCESS
JAVA_HOME=/usr/lib/jvm/java-21-openjdk mvn -pl inventory-service,order-service,api-gateway test
JAVA_HOME=/usr/lib/jvm/java-21-openjdk mvn -pl inventory-service spotless:check
```

| Alcance | Tests | Fallos | Omitidos |
|---|---:|---:|---:|
| `inventory-service` unit + slice JPA | 41 | 0 | 0 |
| `inventory-service` IT con **Postgres 15 real** | 5 | 0 | 0 |
| `order-service` | 63 | 0 | 0 |
| `api-gateway` | 7 | 0 | 0 |
| **Total reactor** | **111** | **0** | **0** |

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

## 5. Único hueco abierto — B4-06 (⚠️ WARN)

B4-06 (tests de integración full-stack) quedó **pausada** por diseño hasta que B4-07 y B4-08 cerraran; esas
dos ya cerraron, así que B4-06 está desbloqueada pero **incompleta**. Lo ya existente y correcto:

- Dependencias Testcontainers + failsafe en `pom.xml` ✔
- `InventoryControllerTest` con 22 casos unitarios ✔
- `InventoryReservationIT` con 5 casos de integración ✔
- Concurrencia de 20 hilos ✔ (en el IT y en el slice H2)
- Idempotencia de `/reserve` en IT ✔
- CI con `mvn verify` ✔ (B4-12)

**Faltan 4 escenarios del spec S3 de B4-06 a nivel de IT:**

1. `/release` idempotente en Postgres real (2ª llamada → 200, stock no cambia) — hoy solo hay test de
   servicio con mocks (`repeatedReleaseDoesNotRestoreStockAgain`).
2. Flujo completo `reserve → release` con los 2 movimientos en `stock_movement_history`.
3. `POST /reserve` de un producto inexistente → 404.
4. `POST /reserve` sin stock suficiente → 400 `available:false` con `remainingStock`.

Además, el ítem "`mvn verify` pasa **en CI**" no es verificable desde el entorno local (requiere GitHub
Actions); queda como comprobación pendiente del pipeline.

**Nada de esto invalida los PASS de las demás ODD**: B4-06 no bloquea a ninguna y su hardware (contenedor,
failsafe, CI) ya está en pie.

---

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
