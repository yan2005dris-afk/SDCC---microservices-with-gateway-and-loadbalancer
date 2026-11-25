# B6-01 — Apply Progress

**Feature:** ODD — B6-01: Catálogo Paginado (INV-07)
**Spec:** `odd/archive/B6-01-inventory-catalog.md`
**Rework brief:** `odd/verification/B6-01-rework-brief.md`
**Verification:** `odd/verification/B6-01-verification-2026-11-25.md`, `odd/verification/B6-01-rework-verification-2026-11-25.md`
**Branch:** `brydyan/feature/inventory-stock-mov-restock`
**Status:** implemented and verified — `mvn verify` green (unit + integration against real PostgreSQL)

## Tasks

- [x] A — `InventoryController#listProducts(Pageable)` created (`GET /api/inventory`)
- [x] B — No change required: `InventoryProductRepository` already extends `JpaRepository`, which extends `PagingAndSortingRepository`; `findAll(Pageable)` used
- [x] C — Page size capped at 100 inline in controller; default `productCode ASC`, client sort preserved
- [x] D — Tests added for pagination, default page metadata, sort passthrough, size cap, page number, sort allowlist

## Rework (B6-01 rework brief)

- [x] Task A — added `listProductsUsesRequestedPageNumber`; M8 mutant (`pageable.getPageNumber()` → `0`) RED, revert GREEN; mutant not left applied
- [x] Task B — `mvn clean verify` green with JDK 21 and Docker (rootless daemon: rootlesskit 3.3.0 + slirp4netns 1.3.7, storage-driver `vfs`). Unit phase green, `InventoryReservationIT` 14/14 green against real PostgreSQL 15. DoD item closed
- [x] Task C — chose **filter** (not 400): unknown sort properties discarded; if none remain, `productCode ASC`; endpoint stays 200. Added tests for `sort=bogus` and mixed `sort=name,desc&sort=bogus,asc`

## DoD

- [x] `GET /api/inventory` returns paged `ProductResponse` content
- [x] `size` max 100
- [x] Order: `productCode ASC` by default, client `sort` preserved (known properties only)
- [x] Tests: `page=0 size=20`, content correct, `totalElements`, `page>0`
- [x] `mvn verify -pl inventory-service` passes — verified 2026-11-25 (JDK 21 + rootless Docker): BUILD SUCCESS, unit phase green, `InventoryReservationIT` 14/14 green against real PostgreSQL 15 (`postgres:15-alpine` via Testcontainers)

## Tests

| Stage | Tests | Failures | Errors |
|---|---:|---:|---:|
| Before (base `281621d`, JDK 21) | 51 | 0 | 0 |
| After initial implementation (`7f8d505`, JDK 21) | 55 | 0 | 0 |
| Before rework (`d97f717`, JDK 21) | 55 | 0 | 0 |
| After rework (`9e50b5e`, JDK 21) | 58 | 0 | 0 |
| RED 1 (no endpoint) | 3 | 3 | 0 |
| GREEN 1 (endpoint added) | 3 | 0 | 0 |
| RED 2 (no size cap) | 4 | 1 | 0 |
| GREEN 2 (size cap added) | 4 | 0 | 0 |
| RED M8 (page forced to 0) | 1 | 1 | 0 |
| GREEN M8 (mutant reverted) | 1 | 0 | 0 |
| RED sort allowlist (unknown/mixed props) | 2 | 2 | 0 |
| GREEN sort allowlist (filtered) | 2 | 0 | 0 |
| Verify with real PostgreSQL (`mvn verify`, JDK 21 + rootless Docker) | 67 unit + 14 IT | 0 | 0 |

`mvn test` (from `src/inventory-service`, JDK 21): 58 tests, 0 failures, 0 errors.
`mvn clean verify` (from `src/inventory-service`, JDK 21): **BUILD SUCCESS**. Unit phase green;
`InventoryReservationIT` 14/14 green against a real PostgreSQL 15 container (`postgres:15-alpine`).
The counts in the "Verify" row above (67 unit) already include the B6-02 restock tests that landed
on this branch afterwards; the B6-01 subset is the 58 unit tests recorded in the previous row.
`InventoryReservationIT` was executed twice: 1 test class, 14 test methods, 0 failures, 0 errors.

## Commits

- `71a9fd5` feat(inventory): add paginated product catalog endpoint
- `7f8d505` fix(inventory): cap catalog page size at 100
- `d97f717` docs(odd): record B6-01 implementation progress
- `79d798c` test(inventory): cover requested page number in catalog endpoint
- `9e50b5e` fix(inventory): validate catalog sort properties against allowlist
- `a3993e1` docs(odd): update B6-01 apply progress with verify results
- `59cae46` docs(odd): record rework review assessment
- `781ee7b` docs(odd): add B6-01 verification artifacts (this file, brief and verification reports)

## Gaps / Notes

- Spec says `ProductResponse` comes from B4-04, but B4-04 defines `ReservationResponse`; no `ProductResponse` existed in the repo. Created `dto/ProductResponse.java` from the record in the spec.
- Added `dto/PagedResponse.java` because Spring's default `Page` JSON has `number`, not the documented `currentPage` field.
- ODD header names branch `brydyan/feature/inventory-inventory-stock-mov-restock`; working branch is `brydyan/feature/inventory-stock-mov-restock`. Header not edited (explicitly out of scope).
- Build requires JDK 21 here: default JDK 25 makes Byte Buddy/Mockito fail with "Java 25 (69) is not supported by the current version of Byte Buddy" (pre-existing, reproduced on base with 25 errors).
- RDD assess (`gentle-ai review assess --base-ref 281621d --json`): `risk=medium`, `review_due=false`, `reason=under_budget`. No review started.
- RDD assess for rework (`--base-ref d97f717`, untracked verification/task files excluded): `risk=medium`, `review_due=false`, `reason=under_budget`. No review started.
- The DoD `mvn verify` was closed on 2026-11-25 with a **rootless Docker daemon** because this machine has no root access to start the system daemon: `rootlesskit` 3.3.0 + `slirp4netns` 1.3.7 (Arch `extra` packages extracted without root), `dockerd-rootless.sh` from moby/moby, `--storage-driver=vfs`, socket at `$XDG_RUNTIME_DIR/docker.sock` (`DOCKER_HOST=unix://$XDG_RUNTIME_DIR/docker.sock`). Testcontainers picks it up from `DOCKER_HOST`. No repository file was changed to achieve this; it is only an environment recipe.
- CI already covers the same command: `.github/workflows/ci-inventory-service.yml` runs `mvn -f src/pom.xml verify -pl inventory-service` on `ubuntu-latest`, where Docker is available.
- Risk: none (additive endpoint and sort filtering; existing `GET /{productCode}`, `/reserve`, `/release` behavior covered by unchanged tests).

Ready for QA review or merge once integration environment has Docker/PostgreSQL.
