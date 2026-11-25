# B6-01 — Verificación del rework (cierre del WARN)

**Spec de referencia:** `odd/verification/B6-01-rework-brief.md` (brief de rework)
**Spec ODD original:** `odd/archive/B6-01-inventory-catalog.md`
**Apply progress:** `odd/archive/B6-01-apply-progress.md`
**Rama:** `brydyan/feature/inventory-stock-mov-restock`
**Base del rework:** `d97f717` · **HEAD:** `59cae46`
**Fecha:** 2026-11-25
**Entorno:** JDK 21, Maven 3.10.0, Docker NO disponible
**Veredicto:** ✅ **PASS** — rework completo y `mvn verify` verde contra PostgreSQL real (cierre 2026-11-25, ver §8)

> Nota de alcance: la ruta invocada (`verification/B6-01-rework-brief.md`) es el brief de rework,
> no un ODD con `proposal/design/specs/tasks`. La verificación se hizo contra el brief (3 tareas: A, B, C)
> y contra el ODD B6-01 como contrato de fondo.

---

## 1. Completitud de tareas del brief

| Tarea | Estado | Evidencia verificada |
|---|---|---|
| A — cerrar mutación M8 (`page > 0`) | [x] | Test `listProductsUsesRequestedPageNumber` añadido en `InventoryCatalogControllerTest.java`. **Mutante reproducido por el verificador**: parcheando `pageable.getPageNumber()` → `0`, ese test falla (`Actual invocations have different arguments`) y el resto no. Mutante revertido, el código en HEAD usa `pageable.getPageNumber()`. |
| B — cerrar `mvn verify` | [x] | **Cerrado 2026-11-25.** Se levantó un daemon Docker **rootless** (rootlesskit 3.3.0 + slirp4netns 1.3.7, `--storage-driver=vfs`, `DOCKER_HOST=unix://$XDG_RUNTIME_DIR/docker.sock`), porque esta máquina no permite arrancar el daemon del sistema sin root. Con él: `mvn verify` → **BUILD SUCCESS**, `InventoryReservationIT` **14/14 verde contra PostgreSQL 15 real** (`postgres:15-alpine` vía Testcontainers), fase unitaria verde. El bloque de código del catálogo no cambió con B6-02 (`git diff 152314e..HEAD` solo añade el endpoint de restock), así que la evidencia aplica a B6-01. Detalle en §8. |
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
4. **El DoD de `mvn verify` quedó cerrado** el 2026-11-25 (ver §8): daemon Docker rootless local.
   En CI el mismo comando corre en `ubuntu-latest`, donde Docker está disponible
   (`.github/workflows/ci-inventory-service.yml`).

## 7. Veredicto

**✅ PASS** — El rework cierra los puntos de código (Tarea A con mutante reproducido
independientemente, Tarea C con 2 tests que matan el mutante), 12/12 mutaciones cubiertas,
58/58 tests verdes, 0 regresiones, 4 commits conventional sin trailers de IA, sin trabajo lateral.
La Tarea B (integración) se cerró después con evidencia real: ver §8.

Además, los 2 hallazgos menores de documentación (referencia colgante al brief y commit `59cae46`
ausente de la lista) quedaron resueltos en los commits `781ee7b` y `152314e`.

---

## 8. Cierre del DoD `mvn verify` (2026-11-25)

El bloqueo era ambiental: el daemon Docker del sistema necesita root y esta máquina no lo da.
Solución aplicada, **sin tocar ningún archivo del repositorio**: daemon Docker *rootless*.

**Receta (entorno local, sin root):**
```bash
# 1. Binarios rootless (paquetes Arch extra, extraídos sin root)
rootlesskit 3.3.0 + slirp4netns 1.3.7  ->  <dir>/usr/bin
dockerd-rootless.sh  (moby/moby contrib) + docker 29.9.3 static  ->  <dir>

# 2. Daemon rootless (ruta corta: los sockets unix tienen límite de ~108 chars)
export PATH=<dir>:<dir>/usr/bin:$PATH
export XDG_RUNTIME_DIR=/tmp/rdl
./dockerd-rootless.sh --storage-driver=vfs --data-root=/tmp/rdl/data --exec-root=/tmp/rdl/exec

# 3. Testcontainers lo toma de DOCKER_HOST
export DOCKER_HOST=unix:///tmp/rdl/docker.sock
docker pull postgres:15-alpine
```

**Resultado del comando del DoD:**
```bash
cd src/inventory-service
JAVA_HOME=/usr/lib/jvm/java-21-openjdk mvn verify
```
```
[INFO] Tests run: 67, Failures: 0, Errors: 0, Skipped: 0        <- fase unit (incluye B6-02)
[INFO] Creating container for image: postgres:15-alpine
[INFO] Tests run: 14, Failures: 0, Errors: 0, Skipped: 0        <- InventoryReservationIT
[INFO] BUILD SUCCESS
```

`InventoryReservationIT` se ejecutó contra **PostgreSQL 15 real** (no H2, no mock): 14 métodos,
0 fallas, 0 errores.

**Aplica a B6-01:** `git diff 152314e..HEAD -- src/inventory-service` solo añade el endpoint de
restock de B6-02 (nuevo constructor con `InventoryRestockService` + `PUT /{productCode}/stock`).
El bloque `listProducts` / `resolveSort` y `InventoryCatalogControllerTest` no cambian, así que la
evidencia de `mvn verify` cubre el código de B6-01 tal como está en HEAD.

**Nota:** el CI ya ejecuta este mismo comando
(`.github/workflows/ci-inventory-service.yml` → `mvn -f src/pom.xml verify -pl inventory-service`
en `ubuntu-latest`), donde Docker está disponible de serie.
