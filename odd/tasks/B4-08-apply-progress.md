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
