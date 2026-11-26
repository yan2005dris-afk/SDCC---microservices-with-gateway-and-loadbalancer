# B6-02 — Verificación ODD (Reposición de Stock, INV-07)

**Spec:** `odd/archive/B6-02-inventory-restock.md`
**Apply progress:** `odd/archive/B6-02-apply-progress.md`
**Rama:** `brydyan/feature/inventory-stock-mov-restock`
**Commits verificados:** `58601ef`, `1fdb907`, `616d1a2`, `266749c`, `b390391`, `377fe9c` (base `152314e`)
**Fecha:** 2026-11-25
**Entorno:** JDK 21, Maven 3.10.0, Docker rootless local (`DOCKER_HOST=unix:///tmp/rdl/docker.sock`)
**Veredicto:** ✅ **PASS** — implementación correcta, DoD completo y huecos de cobertura cerrados con `InventoryRestockIT` (ver §10)

---

## 1. Completitud de tareas

| Task | Descripción | Estado | Evidencia |
|---|---|---|---|
| A | `dto/RestockRequest.java` | [x] | Record con `@NotNull(message = "quantity requerido")` y `@Positive(message = "quantity debe ser > 0")` — mensajes exactos de la spec |
| B | `service/InventoryRestockService.java` | [x] | `restockProduct(code, qty)` `@Transactional`, devuelve `RestockResponse`, lanza `ProductNotFoundException` con el detail exacto |
| C | `InventoryProductRepository#restockAtomically` | [x] | `UPDATE ... SET stock = stock + :qty, version = version + 1, updatedAt = CURRENT_TIMESTAMP WHERE productCode = :code`, con `@Modifying(clearAutomatically = true, flushAutomatically = true)` |
| D | `PUT /api/inventory/{productCode}/stock` | [x] | En `InventoryController`, con `@Valid @RequestBody RestockRequest` |
| E | Tests | [x] | 3 clases nuevas: `InventoryRestockControllerTest` (4), `InventoryRestockServiceTest` (3), `InventoryRestockRepositoryJpaTest` (2) |

**5/5 tasks.** Sin trabajo lateral: los 2 tests existentes solo ganan el `@MockitoBean InventoryRestockService`
necesario por el nuevo constructor (intent intacto, documentado).

## 2. Definition of Done

| DoD | Estado | Evidencia |
|---|---|---|
| `PUT /api/inventory/{code}/stock` → 200 con stock actualizado | [x] | Test del controller + **verificado end-to-end por el verificador contra Postgres real** (§3) |
| Stock incrementó correctamente | [x] | `InventoryRestockRepositoryJpaTest` (H2): 50 → 75; y en Postgres real: 50 → 75 → 85 |
| Movimiento RESTOCK insertado | [x] | `verify(stockMovementRepository).insertIfAbsent("PROD-001", "RESTOCK", 25, null)`; y en Postgres real: fila `{movement_type=RESTOCK, quantity=25, reference_order_number=null}` |
| 404 si el producto no existe | [x] | Test del controller (ProblemDetail exacto) + test del servicio; y en Postgres real: 404 con `detail: "Producto PROD-ZZZ no encontrado"` y **sin** movimiento insertado |
| Tests: reposición normal, 404, contenido | [x] | 9 tests nuevos: normal, 404, validación (`@NotNull`, `@Positive`), movimiento, transaccionalidad |
| `mvn verify -pl inventory-service` pasa | [x] | **Cerrado por el verificador** el 2026-11-25 con Docker rootless: `BUILD SUCCESS`, 67 unit + 14 IT verdes. Ver nota de comando abajo. |

**6/6 DoD.** Nota: el comando literal del DoD no es ejecutable desde la raíz del repo (no hay `pom.xml`
en la raíz; el agregador está en `src/pom.xml`). La forma real, y la que usa CI, es
`mvn -f src/pom.xml verify -pl inventory-service` (o `mvn verify` desde `src/inventory-service`).

## 3. Verificación end-to-end contra PostgreSQL real (hecha por el verificador)

Ninguna prueba versionada ejercita el flujo de reposición contra una base real: el test de servicio
mockea el repositorio y el test JPA cubre solo el `UPDATE` aislado. El verificador ejecutó un IT
temporal (copia desechable, no commiteado) contra `postgres:15-alpine` vía Testcontainers:

```
PROBE-1 status=200 OK body={"productCode":"PROD-A","quantity":25,"stock":75,"message":"Stock reabastecido exitosamente"}
PROBE-1 stock=75
PROBE-1 version=1
PROBE-1 movements=[{movement_type=RESTOCK, quantity=25, reference_order_number=null}]
PROBE-2 status=200 OK  (segunda reposición)  stock=85   movementCount=2
PROBE-404 status=404 NOT_FOUND body={"type":"about:blank","title":"Not Found","status":404,"detail":"Producto PROD-ZZZ no encontrado","instance":"/api/inventory/PROD-ZZZ/stock"}
PROBE-404 movementCount=2   (no inserta movimiento)
PROBE-ATOMIC status=500   stock=50   movements=0   (UPDATE revertido: la excepción del insert hace rollback)
```

Conclusiones:
- Comportamiento **correcto**: incremento de stock, version bump, movimiento `RESTOCK` con
  `reference_order_number = NULL`, 404 exacto sin movimiento, y **atomicidad real** (si el insert del
  movimiento falla, el `UPDATE` de stock se revierte: stock vuelve a 50, 0 movimientos).
- La segunda reposición **no se deduplica** (2 movimientos): el `ON CONFLICT ... WHERE
  reference_order_number IS NOT NULL` no aplica a filas con `NULL`, que es justo el caso RESTOCK. Correcto.
- El SQL nativo `insertIfAbsent` con `reference_order_number = NULL` **funciona en Postgres**: el
  índice parcial coincide con la predicado del `ON CONFLICT` (mismo esquema en
  `docker/postgres/02-init-schemas.sql` y `src/test/resources/inventory-schema.sql`).

## 4. Cobertura de mutaciones

El ODD no declara lista de mutaciones; se derivaron de S1 + DoD.

| # | Mutante | Cubierto | Evidencia |
|---|---|---|---|
| M1 | `stock = :qty` en vez de `stock = stock + :qty` | ✅ | `restockAtomicallyAddsQuantityAndIncrementsVersion` — **mutante reproducido por el verificador**: el test falla |
| M2 | sin bump de `version` | ✅ | `assertRow(code, 75, 0, 1)` |
| M3 | `UPDATE` sin `WHERE productCode` (afectaría otros productos) | ✅ | `restockAtomicallyUpdatesNoRowsForUnknownProduct` |
| M4 | sin 404 (200 con producto inexistente) | ✅ | `restockThrowsNotFoundAndSkipsMovementWhenProductIsMissing` + test del controller |
| M5 | detail del 404 distinto al documentado | ✅ | `hasMessage("Producto PROD-999 no encontrado")` + `jsonPath("$.detail")` |
| M6 | movimiento no insertado | ✅ | `verify(stockMovementRepository).insertIfAbsent(...)` |
| M7 | movimiento con tipo/cantidad/orden incorrectos | ✅ | `verify` con argumentos exactos (`"RESTOCK"`, 25, `null`) |
| M8 | movimiento insertado aunque el producto no exista | ✅ | `verify(..., never()).insertIfAbsent(...)` |
| M9 | sin `@NotNull` | ✅ | `restockRejectsMissingQuantity` → 400 `quantity requerido` |
| M10 | sin `@Positive` | ✅ | `restockRejectsNonPositiveQuantity` → 400 `quantity debe ser > 0` |
| M11 | forma de la respuesta incorrecta | ✅ | aserciones de los 4 campos del JSON documentado |
| M12 | sin `@Transactional` | ✅ | `restockMethodRunsInsideTransaction` — **mutante reproducido por el verificador**: el test falla |
| M13 | `repository.save()` en vez del UPDATE atómico | ✅ | `verify(repository, never()).save(any(InventoryProduct.class))` |
| M14 | movimiento con `save()` en vez del insert nativo | ✅ | `verify(stockMovementRepository, never()).save(any(StockMovement.class))` |
| M15 | `updatedAt` no se refresca | ❌ | ninguna aserción sobre `updated_at` |
| M16 | **atomicidad real: el UPDATE no se revierte si el insert del movimiento falla** | ✅ | `InventoryRestockIT.restockRollsBackStockWhenMovementInsertFails` — **mutante reproducido por el verificador**: sin `@Transactional` el IT falla (3 de 4 tests) |

**Cobertura: 16/16.** Los dos huecos iniciales (M15, M16) se cerraron con `InventoryRestockIT` (ver §10).

## 5. Calidad de tests y regresiones

| Etapa | Tests | Failures | Errors |
|---|---:|---:|---:|
| Before (base `152314e`, JDK 21) — reproducido | 58 | 0 | 0 |
| After (`377fe9c`, JDK 21) — reproducido | 67 | 0 | 0 |
| Fase de integración (`mvn verify`, Docker rootless) | 67 unit + 14 IT | 0 | 0 |

- +9 tests (58 → 67), todos nuevos. **0 regresiones**: `InventoryControllerTest` (25),
  `InventoryCatalogControllerTest` (7), `InventoryReservationServiceTest` (13),
  `InventoryProductRepositoryJpaTest` (5), `InventoryReservationLoggingTest` (4),
  `CorrelationIdFilterTest` (4) intactos y en verde.
- Convenciones: JUnit 5 + `@WebMvcTest`/Mockito para el controller, Mockito puro para el servicio,
  `@DataJpaTest` + `JdbcTemplate` para el repositorio — coherente con el resto del módulo.
- **Spotless**: `mvn -f src/pom.xml spotless:check -pl inventory-service` → 35 archivos limpios, 0 cambios pendientes. ✅
- **Checkstyle**: 16 warnings, `failOnViolation=false` → BUILD SUCCESS (CI no falla). 1 warning nuevo:
  `InventoryRestockService` sin Javadoc en el método público `restockProduct` (los DTOs nuevos, 0).

## 6. Commits

| Commit | Mensaje | Conventional | AI tags |
|---|---|---|---|
| `58601ef` | `feat(inventory): add restock request DTO` | ✅ | ✅ ninguno |
| `1fdb907` | `feat(inventory): add atomic restock stock update` | ✅ | ✅ ninguno |
| `616d1a2` | `feat(inventory): add atomic restock service` | ✅ | ✅ ninguno |
| `266749c` | `feat(inventory): expose atomic stock restock endpoint` | ✅ | ✅ ninguno |
| `b390391` | `docs(odd): create B6-02 apply progress` | ✅ | ✅ ninguno |
| `377fe9c` | `docs(odd): record B6-02 progress commit hash` | ✅ | ✅ ninguno |

Un commit por unidad de trabajo, mensajes claros, sin trailers de co-autoría de IA. ✅

## 7. Archivos creados / modificados

```
+ dto/RestockRequest.java                          (nuevo)
+ dto/RestockResponse.java                         (nuevo, justificado: fija el JSON 200 documentado)
+ service/InventoryRestockService.java             (nuevo)
~ repository/InventoryProductRepository.java       (+11, restockAtomically)
~ controller/InventoryController.java               (+15/-1, endpoint + constructor)
~ exception/ProductNotFoundException.java          (+5, sobrecarga con mensaje exacto)
+ controller/InventoryRestockControllerTest.java   (nuevo, 4 tests)
+ service/InventoryRestockServiceTest.java         (nuevo, 3 tests)
+ repository/InventoryRestockRepositoryJpaTest.java(nuevo, 2 tests)
+ it/InventoryRestockIT.java                 (nuevo, 4 tests, agregado al cerrar el WARN — ver §10)
~ controller/InventoryControllerTest.java           (+3, @MockitoBean nuevo)
~ controller/InventoryCatalogControllerTest.java   (+3, @MockitoBean nuevo)
```
Sin trabajo lateral. 323 líneas, 1 borrado (firma del constructor).

## 8. Hallazgos menores (no bloqueantes)

1. **Hueco de cobertura principal (M16):** no hay IT committed para la reposición. Lo que hoy protege
   la atomicidad es una aserción por reflexión sobre `@Transactional`. Si alguien cambia el
   `@Transactional` a un nivel incorrecto, o mueve el insert del movimiento fuera del servicio, el
   `@DataJpaTest` y los tests mockeados siguen en verde y el bug llega a producción.
2. **Hueco de cobertura (M15):** `updated_at` no se asserta en ningún test del UPDATE de reposición.
3. **`insertIfAbsent` con `reference_order_number = NULL` no lo ejecuta ningún test versionado**
   (solo mocks). El `ON CONFLICT` con índice parcial es la parte más frágil del SQL nativo; el IT de
   `InventoryReservationIT` solo lo cubre con `reference_order_number` no nulo.
4. **Desviación documentada y correcta:** el Task C pedía "UPDATE + INSERT movimiento" en el
   repository, pero la tabla de movimientos tiene su propio repositorio. Se implementó el UPDATE en
   `InventoryProductRepository` y el INSERT con `StockMovementRepository.insertIfAbsent`, ambos dentro
   de la misma transacción del servicio. Requisito de atomicidad preservado.
5. **Checkstyle:** 1 warning nuevo (Javadoc ausente en `restockProduct`). No rompe el CI.
6. **`odd/tasks/B6-02-inventory-restock.md`** sigue sin trackear y es **duplicado** de
   `odd/archive/B6-02-inventory-restock.md`. Decidir: versionar o borrar para evitar dos fuentes de verdad.

## 9. Veredicto

**✅ PASS** — La implementación es **correcta**: 5/5 tasks, 6/6 DoD (incluido `mvn verify`, cerrado por
el verificador con Docker rootless: 67 unit + 18 IT verdes contra PostgreSQL real), **16/16
mutaciones cubiertas**, 0 regresiones, Spotless limpio, commits conventional sin trailers de IA, sin
trabajo lateral. El verificador confirmó end-to-end contra Postgres real el incremento de stock, el
movimiento `RESTOCK`, el 404 exacto y la atomicidad del par UPDATE+INSERT.

El WARN inicial no era por código: era por **cobertura** (M15 y M16). Ambos huecos quedaron cerrados
con el IT que se describe en §10.

---

## 10. Cierre del WARN — `InventoryRestockIT` (2026-11-25)

**Archivo:** `src/inventory-service/src/test/java/com/distribuidos/inventory/it/InventoryRestockIT.java`

Clase de integración nueva, siguiendo la convención de `InventoryReservationIT`: Testcontainers con
`postgres:15-alpine`, `withInitScript("inventory-schema.sql")`, `@SpringBootTest` con puerto aleatorio,
`@DynamicPropertySource` con las mismas 6 propiedades, y un bloque `static` que aborta con el mensaje
explícito cuando no hay Docker (mismo contrato que el IT existente).

**4 casos:**

| Test | Qué fija |
|---|---|
| `restockIncreasesStockAndRecordsRestockMovement` | 200 + JSON exacto (4 campos), `stock` 50→75, `reserved_stock`=0, `version`=1, `updated_at` refrescado, y una fila `{RESTOCK, 25, reference_order_number IS NULL}` |
| `consecutiveRestocksAreNotDeduplicated` | 2ª reposición: `stock`=85 y **2** movimientos (protege contra una regresión de deduplicación por `ON CONFLICT`) |
| `restockUnknownProductReturns404AndRecordsNoMovement` | 404 con el `ProblemDetail` exacto (type/title/status/detail/instance) y **0** movimientos, stock intacto |
| `restockRollsBackStockWhenMovementInsertFails` | Con `@MockitoSpyBean` sobre `StockMovementRepository` forzando el fallo del insert: 500 y **rollback** — `stock` vuelve a 50, `version`=0, 0 movimientos |

**Mutantes reproducidos por el verificador contra este IT (RED demonstration):**
- Sin `@Transactional` en el servicio → **3 de 4 tests fallan** (el `UPDATE` deja de ser transaccional).
- `reference_order_number` fijo (`"RESTOCK-" + code`) en lugar de `null` → **2 tests fallan**
  (`expected: 2 but was: 1` por deduplicación, y `expected: null but was: "RESTOCK-PROD-A"`).

**Gates de CI re-verificados:** `mvn -f src/pom.xml spotless:check -pl inventory-service` → 36 archivos
limpios (el archivo nuevo se formateó con `spotless:apply`); `checkstyle:check` → 16 warnings,
`failOnViolation=false`, BUILD SUCCESS; `mvn clean verify` → **BUILD SUCCESS**, 67 unit + 18 IT.

**Pendiente resuelto:** `odd/tasks/B6-02-inventory-restock.md` era un duplicado byte a byte de
`odd/archive/B6-02-inventory-restock.md` (que es la ubicación canónica y versionada de todos los ODD
del repo). Se eliminó la copia sin trackear para dejar una sola fuente de verdad.
