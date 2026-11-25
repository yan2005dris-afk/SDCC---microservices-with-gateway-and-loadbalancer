# B6-02 — Apply Progress

**Feature:** ODD — B6-02: Reposición de Stock (INV-07)
**Spec:** `odd/archive/B6-02-inventory-restock.md`
**Branch:** `brydyan/feature/inventory-stock-mov-restock`
**Status:** implemented; unit suite green (67 tests); integration verify still blocked by missing Docker

## Tasks

- [x] A — Created `dto/RestockRequest.java` with `@NotNull(message = "quantity requerido")` and `@Positive(message = "quantity debe ser > 0")`
- [x] B — Created `InventoryRestockService#restockProduct(code, qty)`; transactional, returns documented `RestockResponse`, throws 404 with exact detail
- [x] C — Added `InventoryProductRepository#restockAtomically(code, qty)`; `UPDATE stock = stock + qty`, version bump, `updatedAt = CURRENT_TIMESTAMP`
- [x] D — Added `PUT /api/inventory/{productCode}/stock` to `InventoryController`
- [x] E — Tests: successful restock, 404, RESTOCK movement recorded, request validation

## DoD

- [x] `PUT /api/inventory/{code}/stock` returns 200 with updated stock
- [x] Stock increases by the requested quantity
- [x] RESTOCK movement inserted in the same service transaction
- [x] 404 when the product does not exist
- [x] Tests: normal restock, 404, response content
- [ ] `mvn verify -pl inventory-service` passes — blocked: Docker unavailable; `InventoryReservationIT` requires PostgreSQL/Testcontainers

## Tests

| Stage | Tests | Failures | Errors |
|---|---:|---:|---:|
| Before (base `152314e`, JDK 21) | 58 | 0 | 0 |
| After A (`58601ef`) | 58 | 0 | 0 |
| After C (`1fdb907`) | 60 | 0 | 0 |
| After B (`616d1a2`) | 63 | 0 | 0 |
| After D/E (`266749c`) | 67 | 0 | 0 |
| RED C — no `restockAtomically` | 2 | 0 | 2 (compile) |
| GREEN C — repository method added | 2 | 0 | 0 |
| RED B — no service/response type | 3 | 0 | 3 (compile) |
| GREEN B — service added | 3 | 0 | 0 |
| RED D — endpoint missing | 4 | 4 | 0 |
| GREEN D — endpoint added | 4 | 0 | 0 |

`mvn test` (from `src/inventory-service`, JDK 21): 67 tests, 0 failures, 0 errors.

`mvn clean verify` (from `src/inventory-service`, JDK 21): unit phase green; `InventoryReservationIT` fails with
"Los tests de integración de inventory-service requieren un PostgreSQL real (Docker en ejecución o un Postgres accesible). Para el ciclo rápido sin Docker usa: mvn test -pl inventory-service".
Missing to close DoD: Docker daemon or reachable PostgreSQL for the IT.

Differential check: existing `InventoryControllerTest` + `InventoryCatalogControllerTest` on base `152314e` and on final tree both report 32 tests, 0 failures, 0 errors. No regression in `GET /api/inventory`, `GET /{productCode}`, `/reserve`, or `/release`.

## Commits

- `58601ef` feat(inventory): add restock request DTO
- `1fdb907` feat(inventory): add atomic restock stock update
- `616d1a2` feat(inventory): add atomic restock service
- `266749c` feat(inventory): expose atomic stock restock endpoint
- (this commit) docs(odd): create B6-02 apply progress

## Gaps / Notes

- Task C says "UPDATE + INSERT movimiento" in the repository, but the movement table has its own `StockMovementRepository`. Implemented the UPDATE in `InventoryProductRepository.restockAtomically` and the INSERT with the existing `StockMovementRepository.insertIfAbsent(..., "RESTOCK", ..., null)` from the service. Both run inside `InventoryRestockService`'s single `@Transactional` method, so the atomicity requirement is preserved.
- Added `dto/RestockResponse.java` (not in the task table) because the spec fixes the 200 JSON body shape and `Map<String,Object>` would not pin it.
- Added an overload `ProductNotFoundException(String productCode, String message)` to emit the exact documented detail `"Producto PROD-999 no encontrado"`. The original constructor and its message are unchanged; all existing callers keep their behavior.
- Adding the new service to the controller constructor required one extra `@MockitoBean InventoryRestockService` in `InventoryControllerTest` and `InventoryCatalogControllerTest`. Test intent unchanged.
- ODD header names branch `brydyan/feature/inventory-inventory-stock-mov-restock`; working branch is `brydyan/feature/inventory-stock-mov-restock`. Header not edited (out of scope).
- DoD command `mvn verify -pl inventory-service` cannot run from repo root: root has no pom. Ran `mvn clean verify` from `src/inventory-service`, same module.
- Build requires JDK 21 here: default JDK 25 breaks Byte Buddy/Mockito with "Java 25 (69) is not supported by the current version of Byte Buddy".
- RDD assess (`--base-ref 152314e`, untracked `odd/tasks/` excluded): `risk=medium`, `review_due=false`, `reason=under_budget`. No review started.
- Risk: none (additive endpoint, additive constructor, new service/DTOs; existing behavior pinned by unchanged tests and differential check).

Ready for QA review or merge once integration environment has Docker/PostgreSQL.
