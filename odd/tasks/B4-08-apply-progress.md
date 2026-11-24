# Apply Progress — B4-08: Idempotencia + Concurrencia en `/reserve` y `/release` (INV-06 rework)

Rama: `brydyan/feature/inventory-reserve-hardening`
Base: `4e391d7`
ODD: `odd/tasks/B4-08-inventory-idempotence-concurrency.md`
Errata: `odd/tasks/B4-08-verification-errata.md`

## Tasks

- [x] A — Búsqueda de movimiento anterior por `(orderNumber, productCode, RESERVATION)` antes de descontar (`985f7c9`)
- [x] B — Movimiento existente → 200 idempotente sin descontar (`985f7c9`)
- [x] C — Sin movimiento → UPDATE atómico + INSERT en la misma transacción (`985f7c9`)
- [x] D — `DataIntegrityViolationException` por carrera → re-leer movimiento ganador y devolver 200 (`985f7c9`)
- [x] E — Test 20 threads concurrentes sobre el mismo `(orderNumber, productCode)` (`8e03fb2`; omitido localmente sin Docker)
- [x] F — Test de camino real de `reserved_stock`: liberar más de lo reservado → 409 controlado (`8e03fb2`; errata E2.4)
- [x] G — Verificar `CHECK (reserved_stock >= 0)` y migrar el índice único a `(order, type, product)` (`985f7c9`)
- [x] H — `mvn verify -pl inventory-service` + `spotless:check` (`8e03fb2`)

## Definition of Done

- [x] Búsqueda de movimiento anterior por `(reference_order_number, movement_type, product_code)`
- [x] Segunda `/reserve` del mismo `orderNumber` y `productCode` → 200 sin descontar
- [x] Mismo `orderNumber`, producto distinto → reserva ambas líneas
- [x] `DataIntegrityViolationException` capturado y tratado como idempotencia
- [x] 20 threads `/reserve` → 20 × 200, stock decrementó 1 vez (IT implementado; ejecución real pendiente de Docker)
- [x] Liberar más de lo reservado → 409 `CONFLICT`, `reserved_stock` nunca negativo
- [x] Constraint `CHECK (reserved_stock >= 0)` presente en `02-init-schemas.sql`
- [x] Índice único `uq_stock_movement_order_product_type` en esquema nuevo y migración para BD ya inicializada
- [ ] Tests concurrencia verdes con Testcontainers PostgreSQL real (4 ITs omitidos: Docker no disponible)
- [x] `mvn verify -pl inventory-service` pasa
- [x] `mvn spotless:check -pl inventory-service` pasa

## Tests

| Alcance | Antes | Después |
|---|---:|---:|
| `inventory-service` (unit) | 26 | 29 (0 failures, 0 errors, 0 skipped) |
| Reactor completo (JDK 21) | 96 | 99 (0 failures, 0 errors, 0 skipped) |
| `inventory-service` IT con Docker | N/A | 4 definidos, 4 skipped (sin daemon) |

Comandos ejecutados:

```
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk
mvn -q spotless:check -pl inventory-service
mvn -q test
mvn verify -pl inventory-service
```

`mvn verify -pl inventory-service`: `BUILD SUCCESS`; surefire 29/29; failsafe 4/4 skipped por `@Testcontainers(disabledWithoutDocker = true)`.

## Desviaciones / TODO / gaps

- Docker no disponible (`failed to connect to the docker API at unix:///var/run/docker.sock`). Los 4 ITs de PostgreSQL real quedan omitidos localmente; hace falta ejecutarlos en entorno con Docker antes de dar QA-ready.
- La errata E2.4 sustituye el escenario original de 50 threads reserve/release por el camino real de sobre-liberación: `release` de cantidad mayor que la reservada → 409 controlado e invariante preservado.
- El guard `p.reservedStock >= :qty` se añadió a `releaseAtomically`; sin él, el CHECK de BD producía 500 por `DataIntegrityViolationException`.
- La idempotencia se keyea por `(orderNumber, productCode)`; el índice anterior `(order, type)` impedía la multi-línea y permitía sobreventa silenciosa.
- `release` mantiene las claves antiguas de respuesta de producto único y añade `orderNumber` y `releases` (respuesta aditiva, no rupturista).
- Sobre `DataIntegrityViolationException` en la carrera del primer INSERT: se re-lee el movimiento ganador y se responde 200 idempotente.

## Migración

En BD ya inicializada:

```
docker exec -i <postgres-primary> psql -U <user> -d inventory_db \
  < docker/postgres/migrate-stock-movement-index.sql
```

El script elimina `uq_stock_movement_ref_type` y crea `uq_stock_movement_order_product_type`.

## Commits

```
985f7c9 feat(inventory): fix stock idempotency and release invariants
8e03fb2 test(inventory): add real-DB stock invariant integration tests
```

---

## Verificación (2026-11-24 11:05) — WARN

Verificación `03-verify-odd` contra `HEAD` (`bd9cc0a`) con **ejecución real**. Docker sigue caído
(`/var/run/docker.sock` no existe), así que los 4 ITs de PostgreSQL quedan `skipped` y la evidencia de base de
datos se obtuvo con **H2 replicando el DDL de `docker/postgres/02-init-schemas.sql`** (mismas columnas, mismos
`CHECK`, mismo índice único `(reference_order_number, movement_type, product_code)`).

Limitación conocida del banco de pruebas: H2 **no** soporta `FOR NO KEY UPDATE`, que es lo que genera
`@Lock(PESSIMISTIC_WRITE)`; en la copia temporal esa query se parcheó a `select ... for update` nativo
(semántica de bloqueo de fila equivalente). **No es un defecto del código**, pero cualquier test de slice con H2
que ejercite ese camino necesita el mismo parche o no corre.

### 1. Task completion

| Task | Estado | Evidencia |
|---|---|---|
| A — buscar movimiento previo por `(order, product, RESERVATION)` | ✅ | probe P1/H1; mutante M4 (idempotencia solo por `orderNumber`) **muere** |
| B — movimiento existente → 200 sin descontar | ✅ | P1 (`PROD-A=45/5` tras repetir), H1 (2ª llamada 200, `remainingStock=45`) |
| C — sin movimiento → UPDATE atómico + INSERT en la misma tx | ✅ | P1, P2 |
| D — `DataIntegrityViolationException` → 200 idempotente | ⚠️ | el catch **existe** pero **no** entrega 200: ver hallazgo H-1 |
| E — 20 threads mismo `(order, product)` → 20 × 200 | ✅ | probe P2: `ok=20 errores=0`, stock `49/1`, 1 movimiento |
| F — liberar más de lo reservado → error controlado | ✅ | P3 (`InvalidStockReleaseException`), H2 (**HTTP 409** con ProblemDetail) |
| G — `CHECK (reserved_stock >= 0)` + índice único `(order, type, product)` + migración | ✅ | `docker/postgres/02-init-schemas.sql:67`, `docker/postgres/migrate-stock-movement-index.sql` |
| H — `mvn verify` + `spotless:check` | ✅ | `BUILD SUCCESS`, `spotless:check` exit 0 |

### 2. Cobertura de mutaciones (10 mutaciones sobre el código real)

| # | Mutación | Resultado |
|---|---|---|
| M1 | quitar el guard `AND p.reservedStock >= :qty` de `releaseAtomically` | ❌ **SOBREVIVE** (gap) |
| M2 | quitar `clearAutomatically` de `reserveAtomically` | ✅ MUERE |
| M3 | quitar `clearAutomatically` de `releaseAtomically` | ✅ MUERE |
| M4 | idempotencia solo por `orderNumber` (el defecto de B4-05) | ✅ MUERE |
| M5 | el catch de DIV relanza siempre | ✅ MUERE |
| M6 | quitar el lock de fila (`findByProductCodeForUpdate`) en `reserve` | ✅ MUERE |
| M7 | quitar `flushAutomatically` de `reserveAtomically` | ❌ SOBREVIVE (gap menor) |
| M8 | desactivar la idempotencia (`idempotent = false`) | ✅ MUERE |
| M9 | quitar el guard "ya existe RELEASE" en `releaseStock` | ✅ MUERE |
| M10 | el catch de DIV de `release` relanza siempre | ❌ SOBREVIVE (gap) |

**8 de 10 mutaciones mueren.** Las dos que sobreviven están documentadas en B4-11. M1 es la importante: **el fix
central de la errata E2.2 (el guard del invariante) no está protegido por ninguna prueba ejecutable en CI** — la
única prueba que lo cubre es el IT de Postgres, que hoy no se ejecuta.

### 3. Regresión de tests

| Alcance | Antes de B4-08 | Después | Estado |
|---|---:|---:|---|
| `inventory-service` (unit) | 26 | **32** (0 fallos, 0 errores, 0 skipped) | ✅ |
| `order-service` | 7 | 7 | ✅ sin regresión |
| `api-gateway` | 63 | 63 | ✅ sin regresión |
| Reactor completo (JDK 21) | 96 | **102** | ✅ |
| ITs con Postgres real | — | 4 definidos, **4 skipped** (sin Docker) | ⚠️ |

Comandos: `mvn -pl inventory-service verify`, `mvn -pl inventory-service,order-service,api-gateway test`,
`mvn -q spotless:check -pl inventory-service` (exit 0).

### 4. Calidad de commits

`985f7c9`, `8e03fb2`, `ad35a14`, `00427bf`, `ea3f151`, `5fd190c`, `97dacc2`, `bd9cc0a`: todos en formato
convencional `type(scope): mensaje`, autor `Andy Alejandro <andy.alejandrovera4290@upse.edu.ec>`, **sin** tags de
IA (`Co-Authored-By`, "Generated with"), un commit por tarea. `odd/` **sí** está versionado (no ignorado).

### 5. Archivos creados / modificados por la implementación

```
docker/postgres/02-init-schemas.sql                      (índice único nuevo)
docker/postgres/migrate-stock-movement-index.sql         (nuevo, migración para BDs existentes)
src/inventory-service/.../exception/InvalidStockReleaseException.java   (nuevo)
src/inventory-service/.../repository/InventoryProductRepository.java
src/inventory-service/.../repository/StockMovementRepository.java
src/inventory-service/.../service/InventoryReservationService.java
src/inventory-service/.../web/ApiExceptionHandler.java                   (handler 409)
src/inventory-service/.../it/InventoryReservationIT.java                 (4 ITs, nuevo)
src/inventory-service/.../repository/InventoryProductRepositoryJpaTest.java (nuevo, slice H2)
src/inventory-service/.../service/InventoryReservationServiceTest.java
src/inventory-service/src/test/resources/inventory-schema.sql            (nuevo)
src/inventory-service/pom.xml                                            (h2 + testcontainers + failsafe)
```

### 6. Evidencia de los probes (H2, esquema real replicado)

```
P1 r1=ReservationResponse[PROD-A, available=true, remainingStock=45, ...]
P1 r2 (mismo order+producto)=ReservationResponse[PROD-A, remainingStock=45, ...]
P1 r3 (mismo order, otro producto)=ReservationResponse[PROD-B, remainingStock=27, ...]
P1 PROD-A=stock=45/reserved=5 PROD-B=stock=27/reserved=3 | movimientos A=1 B=1

P2 ok=20 errores=0 stock=49/reserved=1 movimientos=1

P3 InvalidStockReleaseException: No hay stock reservado suficiente para liberar PROD-A
P3 fila=stock=45/reserved=2 movimientos RELEASE=0

P4 ok=2 errores=0 fila=stock=50/reserved=0 movimientos RELEASE=1        (release concurrente)

H1 primera 200 | repetida 200 (remainingStock=45) | 2a linea 200 (PROD-B remainingStock=27)
H2 release status=409 {"type":"about:blank","title":"Conflict","status":409,
   "detail":"No hay stock reservado suficiente para liberar PROD-A","instance":"/api/inventory/release"}
H3 release 1 -> 200 | release 2 -> 200 (mismo body, no restaura stock otra vez)
```

### 7. Hallazgos que quedan abiertos

**H-1 — El camino de `DataIntegrityViolationException` no entrega el 200 idempotente (task D / errata E3).**
Con `@GeneratedValue(strategy = IDENTITY)` el `INSERT` se ejecuta **de inmediato** en `save()`: la violación del
índice único salta **dentro** del `try`, así que el catch sí se dispara. Pero la transacción queda marcada
*rollback-only*, y ahí se rompe la promesa:

```
P5a patron-sin-flush -> save() duplicado lanzo DataIntegrityViolationException DENTRO del try
                     | excepcion fuera del try: UnexpectedRollbackException
P6 catch disparo; rollback-only=true
P6 commit lanzo: org.springframework.transaction.UnexpectedRollbackException :
   Transaction silently rolled back because it has been marked as rollback-only
```

Y si se fuerza la carrera de verdad (copia temporal **sin** el lock de fila), la re-lectura del movimiento
ganador se hace sobre una sesión envenenada:

```
P7 ok: ReservationResponse[PROD-A, remainingStock=45, ...]
P7 EXCEPTION: org.hibernate.AssertionFailure : null id in com.distribuidos.inventory.model.StockMovement
   entry (don't flush the Session after an exception occurs)
P7 resumen ok=1 excepciones=1 fila=stock=45/reserved=5 movimientos=1
```

Conclusión: **el 200 idempotente de la task D no está disponible**; si la violación se dispara, el resultado es un
**500** (`UnexpectedRollbackException` o `AssertionFailure`), nunca un 200. En producción esto **no se manifiesta**,
porque `findByProductCodeForUpdate` (`PESSIMISTIC_WRITE` sobre la fila de producto) serializa a los competidores
antes de que ambos puedan insertar: el segundo ve el movimiento ya commiteado y responde 200 por el camino
idempotente normal (P2, 20 hilos). O sea, **la garantía real la da el lock de fila, no el índice único ni el
catch**, y eso no está escrito en ningún sitio. → **B4-11**.

**H-2 — El guard del invariante de `release` no tiene prueba ejecutable (mutante M1 sobrevive).**
`releaseAtomically` valida `p.reservedStock >= :qty` en la propia query, que es el fix pedido por la errata E2.2.
Los tests unitarios lo mockean (`releaseAtomically(...) -> 0`) y el slice JPA solo libera exactamente lo reservado.
El único test que lo ejercita de verdad es el IT de Postgres `releaseWithoutEnoughReservedStockReturnsConflictAndKeepsInvariant`,
que hoy queda `skipped`. Si alguien quita el guard, **CI sigue verde**. → **B4-11**.

**H-3 — El IT de `reserve` concurrente no se ejecutó.** Los 4 ITs quedaron `skipped` por falta de Docker. La
evidencia equivalente se obtuvo con H2 (P2, P4) y es consistente, pero el `FOR UPDATE` de Postgres y el `CHECK`
real solo se validan en el entorno con Docker. → **B4-12** (ITs en CI con Testcontainers + build que falla, en vez
de omitir, cuando no hay Postgres).

**H-4 — La respuesta de `/release` cambió de forma (aditiva).** Ahora devuelve `orderNumber` y `releases[]`
además de `productCode`/`quantity`/`message`. No rompe a `order-service` (que no consume `/release`), pero
`API_CONTRACT.md` sigue desactualizado (ya lo estaba por el FAIL de B4-03, con su rework B4-07 pendiente).

### 8. Veredicto

**⚠️ WARN.** Los criterios funcionales de B4-08 se cumplen y están fijados por pruebas que mueren al mutar el
código: la idempotencia es por `(orderNumber, productCode)`, la multi-línea funciona, 20 hilos dan 20 × 200 con el
stock descontado una sola vez, y el release con stock reservado insuficiente devuelve 409 sin romper el
invariante. Los huecos son acotados y ninguno rompe el comportamiento observable hoy: (a) la task D promete un 200
idempotente que el código no puede dar — es código defensivo muerto que solo se activaría con un 500 — y su
ausencia está enmascarada por el lock de fila; (b) el guard del invariante de `release` no tiene red de protección
en CI; (c) los ITs con Postgres no se ejecutaron en este entorno. Por eso **no** es FAIL y **no** se archiva
todavía: queda pendiente cerrar H-1, H-2 y ejecutar los ITs con Docker.

**Rework: `odd/tasks/B4-11-inventory-div-idempotence-guard.md`** (bug fix sobre feature existente → ODD, no SDD:
no hay entidad, dominio ni contrato de API nuevo).

### 9. Recomendaciones

1. **B4-11 / H-1** — Decidir y **documentar** la garantía real de idempotencia: o el camino de DIV devuelve 200 de
   verdad (`ON CONFLICT DO NOTHING` en Postgres, o re-lectura en una transacción nueva), o se elimina el catch y se
   deja explícito que la serialización la garantiza `PESSIMISTIC_WRITE` sobre la fila de producto. Lo que no puede
   seguir es un catch que promete 200 y entrega 500.
2. **B4-11 / H-2** — Añadir el test de slice JPA que fija el guard `p.reservedStock >= :qty` (mutante M1) y el
   test que fija el catch de DIV de `release` (mutante M10).
3. **H-3** — Ejecutar los 4 ITs en un entorno con Docker. Resuelto por **B4-12**: el CI pasa a `mvn verify` (con
   Testcontainers, `ubuntu-latest` ya tiene Docker) y el build **falla con mensaje explícito** si no hay Postgres,
   en vez de omitir los tests en silencio. Sin eso, la protección del `CHECK` real y del `FOR UPDATE` de Postgres
   no está probada.
4. **B4-05 sigue abierta** hasta que se cierren H-1, H-2 y H-3. Los tres hallazgos originales de B4-05 (idempotencia
   cross-producto, release sin guard, check-then-insert) están **corregidos en el código**; lo que falta es la
   prueba y la documentación de la garantía.
5. **H-4** — Actualizar `API_CONTRACT.md` en el rework de B4-07 (ya estaba pendiente por el FAIL de B4-03).
