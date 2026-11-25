# B6-01 — Apply Progress

**Feature:** ODD — B6-01: Catálogo Paginado (INV-07)
**Spec:** `odd/archive/B6-01-inventory-catalog.md`
**Branch:** `brydyan/feature/inventory-stock-mov-restock`
**Status:** implemented; unit suite green; integration verify blocked by missing Docker

## Tasks

- [x] A — `InventoryController#listProducts(Pageable)` created (`GET /api/inventory`)
- [x] B — No change required: `InventoryProductRepository` already extends `JpaRepository`, which extends `PagingAndSortingRepository`; `findAll(Pageable)` used
- [x] C — Page size capped at 100 inline in controller; client sort preserved, default `productCode ASC`
- [x] D — Tests added for pagination, default page metadata, sort passthrough, and size cap

## DoD

- [x] `GET /api/inventory` returns paged `ProductResponse` content
- [x] `size` max 100
- [x] Order: `productCode ASC` by default, client `sort` preserved
- [x] Tests: `page=0 size=20`, content correct, `totalElements`
- [ ] `mvn verify -pl inventory-service` passes — blocked: Docker unavailable, `InventoryReservationIT` requires PostgreSQL/Testcontainers

## Tests

| Stage | Tests | Failures | Errors |
|---|---:|---:|---:|
| Before (base `281621d`, JDK 21) | 51 | 0 | 0 |
| After (HEAD `7f8d505`, JDK 21) | 55 | 0 | 0 |
| RED 1 (no endpoint) | 3 | 3 | 0 |
| GREEN 1 (endpoint added) | 3 | 0 | 0 |
| RED 2 (no size cap) | 4 | 1 | 0 |
| GREEN 2 (size cap added) | 4 | 0 | 0 |

`mvn test` (from `src/inventory-service`, JDK 21): 55 tests, 0 failures, 0 errors.
`mvn verify` (from `src/inventory-service`, JDK 21): fails only in `InventoryReservationIT` with
"Los tests de integración de inventory-service requieren un PostgreSQL real (Docker en ejecución o un Postgres accesible)".

## Commits

- `71a9fd5` feat(inventory): add paginated product catalog endpoint
- `7f8d505` fix(inventory): cap catalog page size at 100

## Gaps / Notes

- Spec says `ProductResponse` comes from B4-04, but B4-04 defines `ReservationResponse`; no `ProductResponse` existed in the repo. Created `dto/ProductResponse.java` from the record in the spec.
- Added `dto/PagedResponse.java` because Spring's default `Page` JSON has `number`, not the documented `currentPage` field.
- ODD header names branch `brydyan/feature/inventory-inventory-stock-mov-restock`; working branch is `brydyan/feature/inventory-stock-mov-restock`. Header not edited.
- Build requires JDK 21 here: default JDK 25 makes Byte Buddy/Mockito fail with "Java 25 (69) is not supported by the current version of Byte Buddy" (pre-existing, reproduced on base with 25 errors).
- RDD assess (`gentle-ai review assess --base-ref 281621d --json`): `risk=medium`, `review_due=false`, `reason=under_budget`. No review started.
- Risk: none (additive endpoint; existing `GET /{productCode}`, `/reserve`, `/release` behavior covered by 25 unchanged tests).

Ready for QA review or merge once integration environment has Docker/PostgreSQL.
