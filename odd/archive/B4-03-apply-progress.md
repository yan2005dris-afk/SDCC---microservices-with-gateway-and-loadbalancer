# Apply Progress — B4-03: Errores Unificados con ProblemDetail (INV-04)

Rama: `brydyan/feature/inventory-reserve-hardening`
Base: `eb27734`

## Tasks

- [x] A — `ApiExceptionHandler` con `@RestControllerAdvice` (`abd9451`)
- [x] B — `MethodArgumentNotValidException` → 400 ProblemDetail (`91dfa66`)
- [x] C — `ProductNotFoundException` → 404 ProblemDetail (`689da56`)
- [x] D — `StockUnavailableException` → 400 custom (`cfd52e8`)
- [x] E — `Exception` catch-all → 500 ProblemDetail (`110d9a5`)
- [x] F — Tests de validación, 404 y stock insuficiente (`6c6bfd1`)

## Definition of Done

- [x] `ApiExceptionHandler` creado con 5 handlers
- [x] Producto no existe devuelve 404 (no 400)
- [x] Validación devuelve ProblemDetail
- [x] Stock insuficiente devuelve 400 custom `available: false`
- [x] Tests pasan
- [x] `mvn verify -pl inventory-service` pasa

## Tests

| Alcance | Antes | Después |
|---|---|---|
| `inventory-service` | 9 | 14 (0 failures, 0 errors) |
| Reactor completo (JDK 21) | 23 | 28 (0 failures, 0 errors) |

`InventoryControllerTest`: 5 → 10. Agregados: `quantity: 0`, GET 404, POST 404, stock insuficiente, catch-all 500. Los tests de validación ahora verifican `type`, `title`, `status`, `detail` e `instance`.

No hubo RED observable: el advice se agregó sobre código ya implementado y los tests refuerzan el contrato.

## Respuestas

- Validación: `ProblemDetail` con `type=about:blank`, `title=Bad Request`, `status=400`, `instance=/api/inventory/reserve`.
- Producto inexistente: `ProblemDetail` 404, `detail=Producto no encontrado: <code>`.
- Stock insuficiente: `{"productCode", "available": false, "remainingStock", "message"}` con status 400.
- Error inesperado: `ProblemDetail` 500, `detail=Error interno del servidor` (sin exponer stack trace).

## Desviaciones / TODO / gaps

- Se agregó un handler para `HttpMessageNotReadableException` → 400 ProblemDetail, no listado en la tabla de tasks. Sin él, el catch-all de `Exception` convertía los type mismatch (`quantity: "abc"`, `quantity: 2.0`) en 500. Total de handlers: 5 (validación, cuerpo ilegible, producto no encontrado, stock insuficiente, catch-all).
- `GET /api/inventory/{code}` ahora lanza `ProductNotFoundException` en vez de devolver 404 vacío, para cumplir el criterio "GET → 404 ProblemDetail".
- El `message` de stock insuficiente es `"Stock insuficiente. Disponible: 2"`. El ejemplo del spec incluye `solicitado: 5`, pero `StockUnavailableException` no transporta la cantidad solicitada (contrato definido en B4-02). No se modificó esa firma.
- El catch-all mapea cualquier excepción MVC no manejada a 500; no hay mapeos específicos para 405 u otros errores Spring. Punto a revisar en QA.
- JDK 25 rompe Spotless 2.44.3 y los mocks de Mockito. Verificación ejecutada con `JAVA_HOME=/usr/lib/jvm/java-21-openjdk`.

## Commits

```
6c6bfd1 test(inventory): assert ProblemDetail and custom stock error
110d9a5 feat(inventory): map unexpected errors to 500 ProblemDetail
cfd52e8 feat(inventory): return custom 400 for insufficient stock
689da56 feat(inventory): return 404 ProblemDetail for missing products
91dfa66 feat(inventory): map validation failures to ProblemDetail
abd9451 feat(inventory): add API exception handler advice
```

---

## Verificación (2026-11-24 08:24) — FAIL

Verificado contra `eb27734` (pre-B4-03) y HEAD con probes MockMvc:

| Petición | pre-B4-03 | HEAD | Correcto |
|---|---|---|---|
| `PUT /api/inventory/reserve` | 405 | **500** | 405 |
| `POST /reserve` con `Content-Type: text/plain` | 415 | **500** | 415 |
| Ruta sin mapping | 404 | **500** | 404 |

Causa: `@ExceptionHandler(Exception.class)` captura también las excepciones estándar de Spring MVC
(`ExceptionHandlerExceptionResolver` corre antes que `DefaultHandlerExceptionResolver`).

Otros hallazgos: el catch-all no loguea la causa raíz; ningún test cubre 405/415/404 ni el
`Content-Type: application/problem+json`; `docs/api-contracts/API_CONTRACT.md` quedó desactualizado.

**Rework:** `odd/archive/B4-07-inventory-error-handling-fix.md` (Opción A: heredar de
`ResponseEntityExceptionHandler`). B4-03 permanece abierta hasta que B4-07 cierre.

### Re-verificación (2026-11-24 09:10)

Probe MockMvc contra HEAD (con los commits posteriores: `a633499`, `22277f8`, `a969dbe`):

```
B4-03 PUT reserve=500 (esperado 405) | POST text/plain=500 (esperado 415)
     | ruta sin mapping=500 (esperado 404) | POST release text/plain=500 (esperado 415)
```

El FAIL **sigue reproduciéndose** en HEAD. Se agrega el 4º caso (`POST /api/inventory/release` con
`text/plain` → 500, esperado 415), que no estaba en la primera tabla de evidencia y ya está cubierto por la
tarea D de `odd/archive/B4-07-inventory-error-handling-fix.md`.

---

## Verificación `03-verify-odd` (2026-11-24 09:40) — FAIL (rework B4-07 pendiente)

**Task completion:** A–F marcadas `[x]`; los 6 handlers existen y el DoD propio de la ODD está cumplido.
**Commits de B4-03:** `abd9451`, `91dfa66`, `689da56`, `cfd52e8`, `110d9a5`, `6c6bfd1` → formato convencional ✅,
**un commit por task** ✅, sin co-author ni tags de IA ✅. Archivos: `ApiExceptionHandler.java` (nuevo),
`InventoryController.java`, `InventoryControllerTest.java` → sin trabajo extra.

### Cobertura de mutación: 6/6 handlers con test que los mata (3 mutaciones ejecutadas)

| Mutación | Tests que caen |
|---|---|
| Quitar `MethodArgumentNotValidException` | **7** (validaciones: no numérico, fraccional, negativo, cero, productCode vacío, quantity ausente, quantity null, orderNumber vacío) |
| Quitar `ProductNotFoundException` + `HttpMessageNotReadableException` | **4** (404 de producto × 2, `quantity:"abc"`, `quantity:2.0`) |
| Quitar `StockUnavailableException` + catch-all `Exception` | **2** (`reserveInsufficientStockReturnsCustomBadRequest` y `unexpectedFailureReturns500ProblemDetail`, ambos por excepción sin manejar) |

`ReservationNotFoundException` usa el mismo mecanismo ya mutado (`ProductNotFoundException`).

### Contrato propio de S1: ✅ funciona (probe HEAD)

```
S1-validacion-abc        -> 400 application/problem+json {"detail":"Cuerpo de solicitud inválido",...}
S1-producto-inexistente  -> 404 application/problem+json {"detail":"Producto no encontrado: PROD-999",...}
S1-stock-insuficiente    -> 400 application/json        {"productCode","available":false,"remainingStock","message"}
S1-error-interno-500     -> 500 application/problem+json {"detail":"Error interno del servidor",...}
```

### Regresión: ❌ FAIL (sin cambios respecto a 09:10)

```
REG-PUT-reserve(esperado 405)           -> 500 application/problem+json
REG-POST-text-plain(esperado 415)       -> 500 application/problem+json
REG-ruta-sin-mapping(esperado 404)      -> 500 application/problem+json
REG-POST-release-text-plain(esperado 415) -> 500 application/problem+json
```

Causa: `@ExceptionHandler(Exception.class)` captura también las excepciones estándar de Spring MVC
(`ExceptionHandlerExceptionResolver` corre antes que `DefaultHandlerExceptionResolver`).

### Otros gaps confirmados

- El catch-all **no loguea** la causa raíz: el `IllegalStateException("boom-para-el-log")` **no aparece en el
  log** (ni mensaje ni stack trace) → el 500 sale sin rastro.
- Ningún test cubre 405/415/404 → el defecto no puede ser detectado por la suite actual.
- `docs/api-contracts/API_CONTRACT.md` §3.1/§3.2 desactualizado: no documenta `orderNumber`, ni `unitPrice` /
  `productName` / `message` del 200 real (B4-04), ni el body real del 400 de stock (`remainingStock` +
  "Stock insuficiente. Disponible: N"), ni ProblemDetail / `application/problem+json`, ni el endpoint
  `POST /api/inventory/release` (B4-05).

### Regresiones de tests

`inventory-service` 22 → **26** (17 controller + 9 service) · reactor **92 → 96** (gateway 7 · order 63 ·
inventory 26), 0 failures. Los 17 tests del controller cubren los 13 previos + 4 de B4-09, ninguno roto.

**Veredicto: ❌ FAIL — B4-03 permanece abierta.** El rework es `odd/archive/B4-07-inventory-error-handling-fix.md`
(Opción A: heredar de `ResponseEntityExceptionHandler` + sobrescribir `handleMethodArgumentNotValid`,
`handleHttpMessageNotReadable` y `handleExceptionInternal`; tests 405/415/404 + `application/problem+json`;
logging del catch-all; actualizar `API_CONTRACT.md`). La ODD de rework **sigue sin implementar**.


## Cierre de los hallazgos (2026-11-24 12:20) — B4-07 implementada y verificada

El rework `odd/archive/B4-07-inventory-error-handling-fix.md` está **implementado y verificado con ejecución real**:

| Gap de este FAIL | Estado | Evidencia |
|---|---|---|
| 405 / 415 / 404 devueltos como 500 | ✅ corregido | `ApiExceptionHandler` hereda de `ResponseEntityExceptionHandler`; `PUT /reserve` → **405**, `text/plain` → **415** en `/reserve` y `/release`, ruta sin mapping → **404**, todos `application/problem+json` |
| El catch-all no loguea la causa raíz | ✅ corregido | `log.error("Error no controlado en {} {}", método, uri, excepción)`; test `unexpectedFailureLogsTheException` con `ListAppender` (nivel `ERROR` + `ThrowableProxy`) |
| Ningún test cubría 405/415/404 | ✅ corregido | 5 tests nuevos en `InventoryControllerTest` + assert de `application/problem+json` en el helper |
| `API_CONTRACT.md` §3 desactualizado | ✅ corregido | sección 3 reescrita (§3.1–§3.5) contra las formas reales; sección 2 sin tocar |

Verificación: `mvn -pl inventory-service verify` → 41 unit/slice + 5 ITs con Postgres real, 0 skipped;
reactor 111 tests en verde; `spotless:check` en verde. Batería de mutación: revertir el handler mata 5 tests.

**B4-03 sigue en `odd/tasks/`** — su cierre (PASS limpio → `odd/archive/`) requiere la re-verificación
`/03-verify-odd`; no se archiva por declaración propia.
