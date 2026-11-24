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
| B4-13 Observabilidad y alineación del plan | — | ✅ **PASS** (verificada el 2026-11-24 13:50) | — | **sí** |

**Veredicto global del bloque: ✅ PASS — las 13 ODDs en PASS limpio.**
B4-06 se completó después de esta primera pasada (ver §5): se escribieron y verificaron los 5 escenarios de
integración que faltaban, y pasó de WARN a PASS. B4-13 (ver §8) cerró los 5 ítems del checklist de B4 que
nunca llegaron a ODD.

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
JAVA_HOME=/usr/lib/jvm/java-21-openjdk mvn verify                              # BUILD SUCCESS (reactor completo, 142 tests)
JAVA_HOME=/usr/lib/jvm/java-21-openjdk mvn -pl inventory-service verify        # BUILD SUCCESS (52 unit/slice + 14 IT)
JAVA_HOME=/usr/lib/jvm/java-21-openjdk mvn -pl inventory-service spotless:check
```

| Alcance | Tests | Fallos | Omitidos |
|---|---:|---:|---:|
| `inventory-service` unit + slice JPA | 52 | 0 | 0 |
| `inventory-service` IT con **Postgres 15 real** | 14 | 0 | 0 |
| `order-service` unit | 63 | 0 | 0 |
| `order-service` IT (Postgres real) | 6 | 0 | 0 |
| `api-gateway` | 7 | 0 | 0 |
| **Total reactor (`mvn verify`)** | **142** | **0** | **0** |

> Cifras de la corrida posterior a B4-13 (2026-11-24 13:45). En la primera pasada del bloque eran 127
> tests (41 unit/slice + 10 IT en `inventory-service`).

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
  disponible); la corrida remota la confirma el pipeline en el próximo push. **→ Confirmado en remoto el
  2026-11-24 14:31** (ver §9).

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

## 8. B4-13 — Observabilidad y alineación del plan (✅ PASS)

B4-13 cerró los **5 ítems del checklist de B4** en `docs/plan/PLAN_IMPLEMENTACION.md` que nunca llegaron a
ODD ni a tarea de ninguna ODD del bloque, más la trazabilidad (INV-01…INV-06) en el plan y en
`TAREAS_PENDIENTES.md`. Verificada el 2026-11-24 13:50 contra **Postgres 15 real**.

### Comportamientos verificados por ejecución

| Comportamiento | Cómo se comprobó | Evidencia |
|---|---|---|
| `X-Instance-Id` en **todas** las respuestas | MockMvc (ruta con mapping, ruta 404) | cabecera presente, empieza por `inventory-service` |
| `X-Correlation-Id` devuelto y propagado | MockMvc con cabecera entrante `trace-abc` | respuesta con `trace-abc` |
| `cid` entrante reused, no regenerado | unit test del filtro | MDC = `trace-123` durante la petición |
| `cid` generado si no llega | unit test del filtro | cabecera no vacía |
| MDC limpio al terminar | unit test del filtro (incluido camino de error) | `MDC.get(...) == null` después de la petición |
| Log `result=OK` con `remaining` | `ListAppender` de logback | `stock.reserve code=PROD-001 qty=5 order=ORD-001 result=OK remaining=45` |
| Log `result=INSUFFICIENT` sin traza | `ListAppender` de logback | `… result=INSUFFICIENT remaining=2`, `throwableProxy == null` |
| `logging.pattern.level` con `cid`/`inst` | arranque real de la app | `ERROR [cid=42634adf-… inst=inventory-service:8082] …` |
| Sin warning de Hibernate por `open-in-view` | arranque real + IT | `spring.jpa.open-in-view is enabled by default` **no aparece** en la salida |
| `open-in-view: false` activo | IT | `Environment.getProperty("spring.jpa.open-in-view") == "false"` |
| El GET sigue serializando sin sesión abierta | IT contra Postgres real | 200 con `productCode`, `stock`, `reservedStock` |
| `idx_stock_movement_product_code` existe | IT contra Postgres real | `pg_indexes.indexdef` con `product_code` y sin `UNIQUE` |
| Contención con stock 5 | IT, 20 hilos reales | **5 × 200**, **15 × 400**, stock 0, reservado 5, **5** movimientos `RESERVATION` |

### Batería de mutaciones dirigida (10/10 mueren)

Cada mutación se aplicó al código real, se ejecutó el test objetivo, y se restauró el archivo. Se verificó
además que **ningún mutante murió por error de compilación** (todos por aserción): un "test rojo" por un
problema de compilación no demuestra cobertura.

| # | Mutación | Test que la detecta | Resultado |
|---|---|---|---|
| M1 | el filtro no pone `X-Instance-Id` | `CorrelationIdFilterTest` + `InventoryControllerTest` | 💀 murió |
| M2 | el filtro no limpia el MDC | `CorrelationIdFilterTest` | 💀 murió |
| M3 | el filtro ignora el `X-Correlation-Id` entrante | `CorrelationIdFilterTest` + `InventoryControllerTest` | 💀 murió |
| M4 | el filtro no pone `X-Correlation-Id` | `CorrelationIdFilterTest` + `InventoryControllerTest` | 💀 murió |
| M5 | el log dice `result=RESERVED` | `InventoryReservationLoggingTest` | 💀 murió |
| M6 | el log omite `remaining=` | `InventoryReservationLoggingTest` | 💀 murió |
| M7 | el log dice `OK` en vez de `INSUFFICIENT` | `InventoryReservationLoggingTest` | 💀 murió |
| M8 | el esquema de test no crea el índice | IT `schemaHasIndexOnStockMovementProductCode` | 💀 murió |
| M9 | `open-in-view` vuelve a `true` | IT `jpaOpenInViewIsDisabled` | 💀 murió |
| M10 | la reserva permite sobreventa (se quita `stock >= :qty`) | IT `twentyConcurrentReservesWithScarceStock…` | 💀 murió |

### Desviaciones

- **Un filtro en vez de dos:** el plan pedía `InstanceIdHeaderFilter` + `CorrelationIdFilter`; se implementó
  **un** `CorrelationIdFilter` que pone ambas cabeceras (mismo patrón que `order-service`). El requisito de
  fondo —"un filtro en todas las respuestas, no en cada método"— se cumple mejor así. Anotado en el plan.
- **`@Import(InstanceIdentity.class)` en `InventoryControllerTest`:** el `@WebMvcTest` carga el filtro y
  necesita su bean; es el mismo apaño de `OrderControllerTest`. Ninguna aserción previa se modificó.
- **Verificación manual de P02** sigue pendiente (es una corrida de P02, de B7/B9): en el plan queda como el
  único checkbox de B4 sin marcar y **no** se marcó como hecho.

## 9. Confirmación en CI remoto (cierra el último ítem pendiente de B4)

Hasta el 2026-11-24 13:50, el único ítem que quedaba abierto del bloque era *"`mvn verify` pasa **en CI**"*:
se había verificado en el equivalente local (mismo comando, mismo JDK 21, Docker disponible), pero sin corrida
remota. Con el push de la rama (`d849148`) y la apertura del **PR #10** (`brydyan/feature/inventory-reserve-hardening`
→ `develop`), la corrida remota existe y está **verde**:

| Check | Workflow | Resultado |
|---|---|---|
| `Lint, Format & Test` | `CI - Inventory Service` (`ci-inventory-service.yml`) | ✅ **success** — `spotless:check` ✔, `checkstyle:check` ✔, `mvn -f src/pom.xml verify -pl inventory-service` ✔ |
| `Replicación y enrutamiento end-to-end` | `CI - Order Service` | ✅ **success** |
| `GitGuardian Security Checks` | GitGuardian | ✅ **success** — 97 commits escaneados, **0 secretos** detectados |

El paso `mvn -f src/pom.xml verify -pl inventory-service` ejecuta la fase `verify`, que incluye **failsafe**:
los 14 ITs de `InventoryReservationIT` corrieron contra un **PostgreSQL 15 real** en el runner de GitHub
Actions (Docker disponible por defecto) y pasaron. **0 tests omitidos**: no hubo `skipped` silencioso.

Estado del PR #10 al confirmarlo: `mergeable: true`, `mergeable_state: clean`, 97 commits, 66 archivos
cambiados, sin conflictos con `develop`.

Con esto, **el bloque B4 no tiene ningún ítem pendiente**: los 13 ítems del checklist de B4 están
implementados y verificados, las 6 tareas de trazabilidad INV-01…INV-06 están cerradas, y la verificación
es por ejecución tanto local como remota.
