# B6-01 — Verificación del rework (cierre del WARN)

**Spec de referencia:** `odd/verification/B6-01-rework-brief.md` (brief de rework)
**Spec ODD original:** `odd/archive/B6-01-inventory-catalog.md`
**Apply progress:** `odd/archive/B6-01-apply-progress.md`
**Rama:** `brydyan/feature/inventory-stock-mov-restock`
**Base del rework:** `d97f717` · **HEAD:** `59cae46`
**Fecha:** 2026-11-25
**Entorno:** JDK 21, Maven 3.10.0, Docker NO disponible
**Veredicto:** ⚠️ **WARN** — rework correcto y completo en código; queda 1 DoD pendiente por entorno (Docker)

> Nota de alcance: la ruta invocada (`verification/B6-01-rework-brief.md`) es el brief de rework,
> no un ODD con `proposal/design/specs/tasks`. La verificación se hizo contra el brief (3 tareas: A, B, C)
> y contra el ODD B6-01 como contrato de fondo.

---

## 1. Completitud de tareas del brief

| Tarea | Estado | Evidencia verificada |
|---|---|---|
| A — cerrar mutación M8 (`page > 0`) | [x] | Test `listProductsUsesRequestedPageNumber` añadido en `InventoryCatalogControllerTest.java`. **Mutante reproducido por el verificador**: parcheando `pageable.getPageNumber()` → `0`, ese test falla (`Actual invocations have different arguments`) y el resto no. Mutante revertido, el código en HEAD usa `pageable.getPageNumber()`. |
| B — cerrar `mvn verify` | [ ] | **Bloqueado por entorno.** Docker ausente (`Could not find a valid Docker environment`). `mvn verify` corre los 58 tests unitarios en verde y solo rompe en `InventoryReservationIT` con `IllegalStateException: Los tests de integración ... requieren un PostgreSQL real`. Coherente con lo declarado en apply-progress; el DoD sigue `[ ]` correctamente, sin marcar lo no verificado. |
| C — allowlist de `sort` | [x] | `InventoryController.resolveSort()` filtra por `SORTABLE_PROPERTIES`; si no queda ninguna válida → `Sort.by("productCode")` (200, no 500). Opción elegida: **filtrar**, documentada en apply-progress. Tests: `listProductsIgnoresUnknownSortProperty` y `listProductsKeepsOnlyKnownSortProperties`. **Mutante reproducido por el verificador**: revirtiendo el filtro a passthrough, fallan exactamente esos 2 tests. |

**Scope respetado.** Solo se tocaron los 3 archivos permitidos por el brief:
`InventoryController.java` (+15/-1), `InventoryCatalogControllerTest.java` (+50),
`B6-01-apply-progress.md`. Cero cambios en `order-service`, `gateway-service`, `compose.yaml`,
`docker/postgres/02-init-schemas.sql` ni en los DTOs `ProductResponse`/`PagedResponse`.

## 2. Cobertura de mutaciones

| # | Mutante | Antes | Ahora | Evidencia |
|---|---|---|---|---|
| M1 | sin cap de tamaño (`size=150` → 150) | ✅ | ✅ | `listProductsCapsRequestedSizeAtOneHundred` |
| M2 | sin sort por defecto (`productCode ASC`) | ✅ | ✅ | `listProductsDefaultsToPageZeroSizeTwentySortedByProductCode` |
| M3 | default de tamaño ≠ 20 | ✅ | ✅ | mismo test |
| M4 | campo `currentPage` renombrado | ✅ | ✅ | aserciones `currentPage` |
| M5 | `totalElements` / `totalPages` incorrectos | ✅ | ✅ | `totalElements`=5, `totalPages`=3 |
| M6 | mapeo del DTO mal mapeado | ✅ | ✅ | aserciones campo a campo |
| M7 | `sort` del cliente ignorado | ✅ | ✅ | `listProductsKeepsClientSortWhenProvided` |
| M8 | **número de página ignorado (`page` → 0)** | ❌ | ✅ | **cerrado**: test nuevo + mutante reproducido por el verificador |
| M9 | cálculo de `totalPages` | ✅ | ✅ | `totalPages`=3 con total=5, size=2 |
| M10 | **allowlist eliminada (passthrough del `Sort`)** | n/a | ✅ | **nuevo**: matado por los 2 tests de sort |
| M11 | **propiedades inválidas no se descartan** | n/a | ✅ | mismo mutante que M10 |
| M12 | **dirección (`desc`) se pierde al filtrar** | n/a | ✅ | `listProductsKeepsOnlyKnownSortProperties` + `listProductsKeepsClientSortWhenProvided` |

**Cobertura: 12/12.** Sin huecos.

## 3. Calidad de tests y regresiones

| Etapa | Tests | Failures | Errors |
|---|---:|---:|---:|
| Base (`281621d`) | 51 | 0 | 0 |
| Implementación inicial (`7f8d505`) | 55 | 0 | 0 |
| Antes del rework (`d97f717`) | 55 | 0 | 0 |
| **HEAD (`59cae46`)** — reproducido por el verificador | **58** | **0** | **0** |

- +3 tests en el rework (55 → 58), todos nuevos, **0 regresiones**: las 6 clases de test previas
  (`InventoryControllerTest` 25, `InventoryReservationServiceTest` 13,
  `InventoryProductRepositoryJpaTest` 5, `CorrelationIdFilterTest` 4,
  `InventoryReservationLoggingTest` 4) intactas y en verde.
- Convenciones coherentes: JUnit 5 + `@WebMvcTest` + MockMvc + `@MockitoBean`.
- RED/GREEN declarados en apply-progress **reproducidos de forma independiente** por el verificador
  (RED M8: 1 falla; RED sort: 2 fallas). No hay mutante dejado aplicado en HEAD.

## 4. Commits

| Commit | Mensaje | Conventional | AI tags | Alcance |
|---|---|---|---|---|
| `79d798c` | `test(inventory): cover requested page number in catalog endpoint` | ✅ | ✅ ninguno | solo test (+20) |
| `9e50b5e` | `fix(inventory): validate catalog sort properties against allowlist` | ✅ | ✅ ninguno | controller + test (+44/-1) |
| `a3993e1` | `docs(odd): update B6-01 apply progress with verify results` | ✅ | ✅ ninguno | apply-progress (+29/-11) |
| `59cae46` | `docs(odd): record rework review assessment` | ✅ | ✅ ninguno | apply-progress (+1) |

Un commit por unidad de trabajo, mensajes claros, sin trailers de co-autoría de IA. ✅

## 5. Archivos creados / modificados

```
 controller/InventoryController.java               | +15/-1   (allowlist de sort)
 controller/InventoryCatalogControllerTest.java    | +50      (3 tests nuevos)
 odd/archive/B6-01-apply-progress.md               | +29/-11  (evidencia del rework)
```
Sin trabajo lateral. `odd/tasks/B6-02-inventory-restock.md` (sin trackear) tiene mtime **00:14:40**,
anterior al brief (**00:16:08**): no es producto de este rework.

## 6. Hallazgos menores (no bloqueantes)

1. **Referencia colgante en un archivo versionado.** `B6-01-apply-progress.md` (committeado) apunta a
   `odd/verification/B6-01-rework-brief.md`, y ese archivo está **sin trackear**. O se versionan los
   artefactos de `odd/verification/` (brief + informe), o se quita la referencia.
2. **Lista de commits incompleta en apply-progress.** No menciona `59cae46` (queda listado solo
   `a3993e1` con el texto "this file"). Un commit más en la lista, nada más.
3. **Allowlist no cubre `id` ni `reservedStock`.** El cliente no puede ordenar por esas columnas.
   Coherente con la allowlist propuesta en el brief; si se consideran útiles, añadirlas (con test).
4. **El DoD de `mvn verify` sigue abierto** y solo puede cerrarse en un entorno con Docker o un
   PostgreSQL accesible (CI con Docker sirve). No es un defecto de código: el IT aborta por diseño
   cuando no hay Docker, con mensaje explícito.

## 7. Veredicto

**⚠️ WARN** — El rework cierra los puntos de código: Tarea A hecha y con mutante reproducido
independientemente, Tarea C hecha con 2 tests que matan el mutante, 12/12 mutaciones cubiertas,
58/58 tests verdes, 0 regresiones, 4 commits conventional sin trailers de IA, sin trabajo lateral.
El único pendiente es la Tarea B, bloqueada por ausencia de Docker en esta máquina, y correctamente
declarada como tal en el apply-progress (DoD `[ ]`, sin marcar lo no verificado).

**Para pasar a ✅ PASS:**
1. Ejecutar `mvn clean verify` con Docker disponible y confirmar `BUILD SUCCESS` con
   `InventoryReservationIT` en verde; marcar el DoD.
2. Versionar `odd/verification/` (brief + este informe) o quitar la referencia colgante del
   apply-progress, y añadir `59cae46` a su lista de commits.
