# B6-01 — Verificación ODD (Catálogo Paginado, INV-07)

**Spec:** `odd/archive/B6-01-inventory-catalog.md`
**Apply progress:** `odd/archive/B6-01-apply-progress.md`
**Rama:** `brydyan/feature/inventory-stock-mov-restock`
**Commits verificados:** `71a9fd5`, `7f8d505`, `d97f717` (base `281621d`)
**Fecha:** 2026-11-25
**Entorno:** JDK 21 (`/usr/lib/jvm/java-21-openjdk`), Maven 3.10.0, Docker NO disponible
**Veredicto:** ⚠️ **WARN** — implementación conforme, 1 DoD pendiente por entorno + 1 hueco de mutación

---

## 1. Completitud de tareas

| Task | Descripción | Estado | Evidencia |
|---|---|---|---|
| A | `InventoryController#listProducts(Pageable)` | [x] | `controller/InventoryController.java:33` — `@GetMapping` en `/api/inventory`, devuelve `PagedResponse<ProductResponse>` |
| B | `PagingAndSortingRepository` en repository | [x] | Sin cambios: `InventoryProductRepository extends JpaRepository` (hereda `PagingAndSortingRepository`); usa `findAll(Pageable)` |
| C | Validar `size <= 100` | [x] | Cap en línea: `Math.min(pageable.getPageSize(), MAX_PAGE_SIZE)` con `MAX_PAGE_SIZE = 100` (`InventoryController.java:20,36`) |
| D | Tests de paginación, size max, contenido | [x] | `controller/InventoryCatalogControllerTest.java` — 4 tests |

Sin tareas fuera de spec. Sin trabajo lateral en el diff (ver §6).

## 2. Definition of Done

| DoD | Estado | Evidencia |
|---|---|---|
| `GET /api/inventory` devuelve página de `ProductResponse` | [x] | Test `listProductsReturnsPagedProductResponse` (jsonPath `content[0].productCode` = `PROD-001`) |
| `size` máximo 100 | [x] | Test `listProductsCapsRequestedSizeAtOneHundred` (`size=150` → `size`=100) |
| Orden `productCode ASC` por defecto | [x] | `Sort.by("productCode")` cuando el cliente no manda `sort`; test `listProductsDefaultsToPageZeroSizeTwentySortedByProductCode` verifica `findAll(pageable(0,20))` con `Sort.by("productCode")` |
| Tests: page=0 size=20, content correcto, totalElements | [x] | Test default: `totalElements`=5, `totalPages`=1, `currentPage`=0, `size`=20 |
| `mvn verify -pl inventory-service` pasa | [ ] | **Pendiente por entorno.** Docker ausente → `InventoryReservationIT` aborta con mensaje explícito (`InventoryReservationIT.java:52-56`). No es defecto de código: la suite unitaria está verde. |

**4/5 DoD cumplidos. El único pendiente es el `verify` (fase de integración), bloqueado por falta de Docker/PostgreSQL.**

## 3. Cobertura de mutaciones

El ODD no declara una lista explícita de mutaciones; se derivaron de los criterios de aceptación de S1 y del DoD.

| # | Mutante | Cubierto | Evidencia |
|---|---|---|---|
| M1 | Quitar el cap de tamaño (`size=150` devuelve 150) | ✅ | `listProductsCapsRequestedSizeAtOneHundred` — falla sin el cap |
| M2 | Quitar el sort por defecto (`productCode ASC`) | ✅ | `listProductsDefaultsToPageZeroSizeTwentySortedByProductCode` — `verify(repository).findAll(pageable(0,20))` con `Sort.by("productCode")` |
| M3 | Default de tamaño ≠ 20 | ✅ | Mismo test: `jsonPath("$.size").value(20)` |
| M4 | Renombrar `currentPage` (p. ej. `number`) | ✅ | `jsonPath("$.currentPage").value(0)` |
| M5 | `totalElements` / `totalPages` incorrectos | ✅ | `totalElements`=5, `totalPages`=3 con size=2 |
| M6 | Mapear mal el DTO (`stock`/`unitPrice`/`description`) | ✅ | Aserciones campo a campo en `content[0]` |
| M7 | Ignorar el `sort` del cliente | ✅ | `listProductsKeepsClientSortWhenProvided` — `Sort.by("name").descending()` preservado |
| M8 | Ignorar el número de página (`page` siempre 0) | ❌ **HUECO** | Todos los tests usan `page=0`. Un mutante que fije `pageable.getPageNumber() → 0` sobrevive |
| M9 | Cálculo de `totalPages` (total / size) | ✅ | `totalPages`=3 con total=5, size=2 |

**Cobertura: 8/9.** M8 sin evidencia.

### Propuesta de cierre (no aplicada — no pedido)
Añadir un test que pida `page=1&size=2` y verifique `findAll(PageRequest.of(1, 2, Sort.by("productCode")))` y `currentPage`=1.

## 4. Calidad de tests y regresiones

| Etapa | Tests | Failures | Errors |
|---|---:|---:|---:|
| Before (`281621d`, JDK 21) — reproducido | 51 | 0 | 0 |
| After (`7f8d505`/HEAD, JDK 21) — reproducido | 55 | 0 | 0 |

- +4 tests, todos nuevos (`InventoryCatalogControllerTest`), 0 tests preexistentes eliminados o modificados → **0 regresiones**.
- Convenciones: JUnit 5 + Spring `@WebMvcTest` + MockMvc + Mockito (`@MockitoBean`), coherente con `InventoryControllerTest`. ✅
- Cobertura: happy path (default, paginado, sort), caso límite (`size=150`), metadata de página. Falta el caso `page > 0` (M8).
- El test es un mock de slice web: **no** hay prueba de integración que verifique que los productos seed `PROD-001..PROD-005` (definidos en `docker/postgres/02-init-schemas.sql:78-82`) aparezcan realmente en la respuesta. Cubierto solo de forma simulada.

## 5. Commits

| Commit | Mensaje | Convencional | AI tags |
|---|---|---|---|
| `71a9fd5` | `feat(inventory): add paginated product catalog endpoint` | ✅ | ✅ ninguno |
| `7f8d505` | `fix(inventory): cap catalog page size at 100` | ✅ | ✅ ninguno |
| `d97f717` | `docs(odd): record B6-01 implementation progress` | ✅ | ✅ ninguno |

Un commit por unidad de trabajo, mensajes claros, sin trailers de IA. ✅

## 6. Archivos creados / modificados

```
 controller/InventoryController.java      | +26  (nuevo endpoint, no destructivo)
 dto/ProductResponse.java                 | +7   (nuevo)
 dto/PagedResponse.java                   | +18  (nuevo)
 controller/InventoryCatalogControllerTest.java | +133 (nuevo)
 odd/archive/B6-01-apply-progress.md      | +52  (doc)
```
Total: 236 líneas, solo adiciones. Sin archivos extra fuera de spec. Sin cambios en `GET /{productCode}`, `/reserve`, `/release` (25 tests preexistentes intactos).

## 7. Hallazgos adicionales (no bloqueantes)

1. **`sort` con propiedad inexistente → 500.** `GET /api/inventory?sort=bogus` llega a la capa JPA y revienta con `PropertyReferenceException` (confirmado empíricamente con `repository.findAll(PageRequest.of(0,20,Sort.by("bogus")))`). Lo captura el `@ExceptionHandler(Exception.class)` de `web/ApiExceptionHandler.java` y responde 500 "Error interno del servidor". Un parámetro de cliente inválido debería ser 400, no 500. No lo exige el ODD; queda como deuda de robustez (validar el `sort` contra una allowlist de propiedades).
2. **Parámetros degenerados, sin crash.** Comprobado empíricamente: `size=0` → 20 (default de Spring), `size=-1` → 20, `page=-1` → 0, `size=101` → 100. Ninguno produce 500. ✅
3. **Desviación de spec documentada:** el ODD dice que `ProductResponse` viene de B4-04, pero B4-04 define `ReservationResponse`; `ProductResponse` no existía y se creó desde el record de la spec. Correcto, y anotado en apply-progress.
4. **Desviación de contrato:** se añadió `PagedResponse` porque el JSON por defecto de `Page` de Spring expone `number`, no `currentPage`. Sin este DTO el contrato de la spec no se cumplía. Anotado en apply-progress.
5. **Nombre de rama en la cabecera del ODD:** el documento dice `brydyan/feature/inventory-inventory-stock-mov-restock`; la rama real es `brydyan/feature/inventory-stock-mov-restock`. Solo cosmético, no se editó el ODD.
6. **JDK:** el build requiere JDK 21; con JDK 25 (default de la máquina) falla Byte Buddy/Mockito ("Java 25 (69) is not supported by the current version of Byte Buddy"). Problema preexistente, reproducido también en la base.

## 8. Veredicto

**⚠️ WARN** — La implementación cumple el ODD en código: 4/4 tasks, 4/5 DoD (el pendiente es `mvn verify`, bloqueado por Docker ausente, no por defecto), 8/9 mutaciones cubiertas, 0 regresiones (51 → 55, todo verde), commits limpios, sin trabajo lateral. Listo para merge tras:
1. Cubrir M8 (test de `page > 0`).
2. Ejecutar `mvn verify -pl inventory-service` en un entorno con Docker (o con un PostgreSQL accesible) para cerrar el DoD pendiente y validar el IT de la suite.
3. Opcional: validar `sort` contra allowlist para devolver 400 en vez de 500.
