# Apply Progress — B4-10: Durabilidad de la reserva atómica (cierre de gaps de B4-02)

Rama: `brydyan/feature/inventory-reserve-hardening`
Base: `ad35a14`
ODD: `odd/archive/B4-10-inventory-atomic-reserve-durability.md`

## Tasks

- [x] A — `@Modifying(clearAutomatically = true, flushAutomatically = true)` en los 2 queries (`985f7c9`, ya aplicado en B4-08)
- [x] B — Test de regresión de slice JPA: la relectura ve el stock nuevo (`ea3f151`)
- [x] C — Test de slice JPA: `qty > stock` → 0 filas, nada cambia (`5fd190c`)
- [x] D — Test de slice JPA: 20 reservas concurrentes → stock 0, reserved 20, version 20 (`97dacc2`)
- [x] E — Comentario del invariante del `remainingStock` en el servicio (`bd9cc0a`)
- [x] F — Dependencia `h2` scope `test` en `inventory-service/pom.xml` (`00427bf`)
- [x] G — DoD de la spec archivada de B4-02 marcado con nota (`f87494c`, verificado sin cambios)
- [x] H — `mvn -pl inventory-service verify` + `spotless:check` en verde (verificado al cierre)

## Definition of Done

- [x] `@Modifying` con `clearAutomatically = true` y `flushAutomatically = true` en los 2 queries
- [x] Test JPA: tras el `UPDATE` masivo, la relectura ve el `stock` nuevo (reserva y liberación)
- [x] Test JPA: `qty > stock` → 0 filas afectadas y la fila queda intacta
- [x] Test JPA: 20 reservas concurrentes → stock 0, `reserved_stock` 20, `version` 20, 0 excepciones
- [x] El invariante del `remainingStock` está escrito en el código
- [x] `h2` en scope `test` en `inventory-service/pom.xml`
- [x] DoD de la spec archivada de B4-02 marcado con nota
- [x] Los tests preexistentes siguen verdes sin modificarlos (29 antes de B4-10)
- [x] `mvn -pl inventory-service verify` pasa
- [x] `mvn -pl inventory-service spotless:check` pasa

## Tests

| Alcance | Antes | Después |
|---|---:|---:|
| `inventory-service` (unit) | 29 | 32 (0 failures, 0 errors, 0 skipped) |
| Reactor completo (JDK 21) | 99 | 102 (0 failures, 0 errors, 0 skipped) |
| `inventory-service` IT con Docker | 4 skipped | 4 skipped (sin daemon; heredado de B4-08) |

Comandos ejecutados:

```
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk
mvn -q spotless:apply -pl inventory-service
mvn -q test -pl inventory-service
mvn -q spotless:check -pl inventory-service
mvn -q verify -pl inventory-service
mvn -q test
```

`mvn -q verify -pl inventory-service`: PASS. Surefire 32/32; failsafe 4/4 skipped por `@Testcontainers(disabledWithoutDocker = true)`.

## Desviaciones / TODO / gaps

- El conteo de la ODD (22 → 25+) era anterior a B4-08. Con B4-08 ya aplicado, el baseline real de esta rama era 29; el cierre queda en 32.
- Las tareas A y G ya estaban resueltas en commits anteriores; no se generaron commits nuevos para ellas.
- La prueba de concurrencia es slice JPA con H2 embebido, como pide la ODD. No sustituye la prueba real con PostgreSQL de B4-06/B4-08.
- Docker sigue sin daemon en este entorno; los 4 ITs de Testcontainers siguen omitidos. No es un bloqueo de B4-10.
- `remainingStock` sigue siendo una segunda lectura dentro de la transacción que mantiene el lock de fila. El comentario en `InventoryReservationService#reservationResponse` fija el invariante. Un `UPDATE ... RETURNING` sería cambio de arquitectura y queda fuera de esta ODD.

## Commits

```
00427bf test(inventory): add H2 dependency for JPA slice tests
ea3f151 test(inventory): pin bulk update cache coherence
5fd190c test(inventory): pin insufficient stock update guard
97dacc2 test(inventory): pin concurrent atomic reserve on H2
bd9cc0a docs(inventory): pin remainingStock transaction invariant
```

---

## Verificación (`03-verify-odd`, 2026-11-24 10:55) — PASS

Verificación contra `HEAD` (`adbec0c`) con **ejecución real**.

### Task completion

| Task | Estado | Evidencia |
|---|---|---|
| A — `@Modifying(clearAutomatically, flushAutomatically)` en los 2 queries | ✅ | `InventoryProductRepository.java` (aplicado en `985f7c9`) |
| B — slice JPA: la relectura ve el stock nuevo | ✅ | `InventoryProductRepositoryJpaTest.bulkUpdateClearsPersistenceContextForReserveAndRelease` (`ea3f151`) |
| C — slice JPA: `qty > stock` → 0 filas, fila intacta | ✅ | `reserveAboveStockUpdatesNoRows` (`5fd190c`) |
| D — slice JPA: 20 reservas concurrentes → 0/20/20 | ✅ | `twentyConcurrentReservesSerializeWithoutOptimisticLockErrors` (`97dacc2`) |
| E — comentario del invariante del `remainingStock` | ✅ | `InventoryReservationService#reservationResponse` (`bd9cc0a`) |
| F — dependencia `h2` scope `test` | ✅ | `inventory-service/pom.xml` (`00427bf`) |
| G — DoD de la spec archivada de B4-02 marcado | ✅ | `f87494c` |
| H — `verify` + `spotless:check` en verde | ✅ | reejecutado en esta verificación |

### Cobertura de mutaciones

| # | Mutación | Resultado |
|---|---|---|
| M2 | quitar `clearAutomatically` de `reserveAtomically` | ✅ MUERE (lo mata `bulkUpdateClearsPersistenceContextForReserveAndRelease`) |
| M3 | quitar `clearAutomatically` de `releaseAtomically` | ✅ MUERE (mismo test) |
| M7 | quitar `flushAutomatically` de `reserveAtomically` | ⚠️ **equivalente** (ver abajo) |

**Sobre M7 (equivalente, no gap):** `flushAutomatically` solo cambia el resultado si hay **entidades dirty** en el
contexto de persistencia. Probe con H2:

```
P10a filas en BD tras save() (sin flush) = 1
     -> save() con IDENTITY inserta de INMEDIATO: no quedan cambios pendientes por crear
P10b (entidad dirty: stock 7 en memoria, flushed antes del UPDATE masivo) -> stock final = 2
M7 (mismo escenario, sin flushAutomatically)                              -> stock final = 2
```

El `auto-flush` de Hibernate antes de una query JPQL de bulk ya vuelca la entidad dirty, de modo que **quitar el
flag no cambia el resultado observable** en este escenario, ni en el flujo actual del servicio (que solo lee la
entidad, nunca la modifica en memoria). El flag queda como redundante-defensivo, no como hueco de cobertura.

### Regresión de tests

| Alcance | Antes | Después | Estado |
|---|---:|---:|---|
| `inventory-service` (unit) | 29 | **32** (0 fallos, 0 errores) | ✅ |
| Reactor completo (JDK 21) | 99 | **102** (0 fallos, 0 errores) | ✅ |
| ITs con Postgres | 4 skipped | 4 skipped | fuera de alcance de B4-10 (por diseño: los tests de esta ODD no requieren Docker) |

Comandos: `mvn -pl inventory-service verify`, `mvn -pl inventory-service,order-service,api-gateway test`,
`mvn -q spotless:check -pl inventory-service` (exit 0).

### Calidad de commits

`00427bf`, `ea3f151`, `5fd190c`, `97dacc2`, `bd9cc0a`: formato convencional `type(scope): mensaje`, autor
`Andy Alejandro <andy.alejandrovera4290@upse.edu.ec>`, **sin** tags de IA, un commit por tarea.

### Archivos creados / modificados

```
src/inventory-service/pom.xml                                                     (h2 test scope)
src/inventory-service/src/test/java/.../repository/InventoryProductRepositoryJpaTest.java  (nuevo)
src/inventory-service/src/main/java/.../service/InventoryReservationService.java    (comentario del invariante)
src/inventory-service/src/main/java/.../repository/InventoryProductRepository.java (flags, ya en B4-08)
odd/archive/B4-02-inventory-atomic-reserve.md                                      (DoD marcado)
```

### Veredicto

**✅ PASS.** Las 8 tasks están implementadas, el DoD está completo, el gap 1 (caché stale) queda **fijado por un
test que muere** si alguien quita `clearAutomatically`, el gap 2 (invariante del `remainingStock`) está escrito en
el código, y el gap 3 (suite solo-mock) tiene ahora slice JPA contra una base real. Sin regresiones (29 → 32 unit,
99 → 102 reactor) y sin tags de IA.

**Se archiva en `odd/archive/`.**

Nota: el gap residual M7 (equivalencia del `flushAutomatically`) y el hecho de que los ITs con Postgres sigan
`skipped` **no** son de B4-10: el primero ya está rastreado en `odd/archive/B4-11-inventory-div-idempotence-guard.md`
y el segundo es tarea de entorno, de B4-06/B4-08.
