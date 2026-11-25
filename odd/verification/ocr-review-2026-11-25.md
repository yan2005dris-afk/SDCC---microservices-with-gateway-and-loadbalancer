# Revisión de código con `ocr review` — 2026-11-25

## Alcance

- Herramienta: OpenCodeReview CLI `v1.14.3` (`ocr review`).
- Rango revisado: `develop..HEAD` (rama `brydyan/feature/inventory-stock-mov-restock`, 25 commits).
- Sesión OCR: `d03871c8-4f1f-442d-a722-b2616c84ce09` (`range`, 9 archivos de código fuente, 3 comentarios).
- Proveedor: `ollama` local (`~/.ocr/config.yaml`), modelo `qwen3-worker-8k`.
  - Nota: la configuración declara `model: qwen2.5-coder:7b`, pero `ocr` resolvió `qwen3-worker-8k`.
- Exclusiones aplicadas por el propio `ocr` (rutas por defecto y exclusiones de usuario): documentación
  (`odd/**`, `docs/**`, `README.md`) y archivos de test.

## Hallazgos y triaje

| # | Archivo | Severidad | Hallazgo | Triaje | Resolución |
|---|---|---|---|---|---|
| 1 | `repository/InventoryProductRepository.java:45` | medium | `restockAtomically` usa `CURRENT_TIMESTAMP` para `updatedAt`; verificar que la columna sea timestamp | **Rechazado (falso positivo)** | Los esquemas (`src/inventory-service/src/test/resources/inventory-schema.sql:11` y `docker/postgres/02-init-schemas.sql:16,55`) definen `updated_at TIMESTAMP WITH TIME ZONE`, compatible con `CURRENT_TIMESTAMP` (`timestamptz`) en PostgreSQL. Verificado además por el IT de reposición (`InventoryRestockIT`), que comprueba `updated_at` tras el `PUT`. |
| 2 | `controller/InventoryController.java:27` | low | `MAX_PAGE_SIZE` está hardcodeado; hacerlo configurable por properties | **Aceptado** | Sustituido por `@Value("${inventory.pagination.max-page-size:100}")`, con clamp defensivo `Math.max(1, ...)` y propiedad declarada en `application.yml` (`INVENTORY_MAX_PAGE_SIZE`, por defecto `100`). Se conserva el comportamiento documentado en el ODD B6-01. |
| 3 | `controller/InventoryController.java:97` | medium | `restockProduct` no contempla que el `productCode` no exista; capturar `ProductNotFoundException` en el controlador | **Rechazado (falso positivo)** | La excepción ya se traduce a `404` de forma global en `web/ApiExceptionHandler.java:89` (`@RestControllerAdvice` + `@ExceptionHandler(ProductNotFoundException.class)`, respuesta `ProblemDetail`). Capturarla en el controlador duplicaría el manejo y perdería el cuerpo de error uniforme. Cubierto por `InventoryRestockControllerTest` y por el caso 404 de `InventoryRestockIT`. |

## Cambios aplicados por el hallazgo 2

- `src/inventory-service/src/main/java/com/distribuidos/inventory/controller/InventoryController.java`
  - constante `MAX_PAGE_SIZE` eliminada; el tope llega por constructor con `@Value`.
  - el valor se normaliza con `Math.max(1, maxPageSize)` para que una configuración inválida
    (`0` o negativa) no provoque un `IllegalArgumentException` en `PageRequest.of`.
- `src/inventory-service/src/main/resources/application.yml`
  - nueva sección `inventory.pagination.max-page-size` (`${INVENTORY_MAX_PAGE_SIZE:100}`).
- `src/inventory-service/src/test/java/com/distribuidos/inventory/controller/InventoryCatalogPageSizeConfigTest.java`
  - test nuevo: con `inventory.pagination.max-page-size=2`, una petición con `size=50` se trunca a 2.
- `README.md`: nota de la propiedad configurable.

## Verificación

| Comprobación | Resultado |
|---|---|
| `mvn -o test` (inventory-service) | **PASS** — 68 unitarios, 0 fallas (67 previos + 1 nuevo) |
| `mvn -o verify` (inventory-service, Docker rootless) | **PASS** — 68 unitarios + 18 IT, 0 fallas |
| `mvn -f src/pom.xml spotless:check -pl inventory-service` | **PASS** (36/36) |
| `mvn -f src/pom.xml checkstyle:check -pl inventory-service` | **PASS** — 16 warnings, mismos que la línea base; `failOnViolation=false` |

Entorno IT: Docker rootless (`DOCKER_HOST=unix:///tmp/rdl/docker.sock`), receta documentada en
`B6-01-rework-verification-2026-11-25.md` §8.

## Veredicto

**PASS.** 1 de 3 hallazgos corregido (mantenibilidad, severidad baja); los 2 restantes se
descartaron por ser falsos positivos verificados contra el código y los esquemas de base de datos.
Sin observaciones de correctitud, seguridad ni rendimiento pendientes.
