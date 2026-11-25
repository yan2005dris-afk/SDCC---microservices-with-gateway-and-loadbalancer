# Apply Progress — B4-07: Corrección de semántica HTTP en el manejo de errores (INV-04 rework)

Rama: `brydyan/feature/inventory-reserve-hardening`
Base: `0b3aa12`
ODD: `odd/archive/B4-07-inventory-error-handling-fix.md`

## Tasks

- [x] A — `ApiExceptionHandler extends ResponseEntityExceptionHandler` (`test(inventory): fix http error semantics`)
- [x] B — Sobrescribir `handleMethodArgumentNotValid`, `handleHttpMessageNotReadable` y `handleExceptionInternal` (`test(inventory): fix http error semantics`)
- [x] C — Handlers de dominio intactos + catch-all con `log.error` (`test(inventory): fix http error semantics`)
- [x] D — Tests 405 / 415 / 404 sin mapping / 415 en `/release` + content type (`test(inventory): fix http error semantics`)
- [x] E — `docs/api-contracts/API_CONTRACT.md` sección 3 actualizada (`docs(api): refresh inventory contract section`)
- [x] F — `mvn verify -pl inventory-service` + `spotless:check` en verde (`test(inventory): fix http error semantics`)

## Definition of Done

- [x] `ApiExceptionHandler` hereda de `ResponseEntityExceptionHandler`
- [x] `PUT /api/inventory/reserve` → 405 ProblemDetail
- [x] `POST /reserve` con `text/plain` → 415 ProblemDetail
- [x] Ruta sin mapping → 404 ProblemDetail
- [x] `POST /release` con `text/plain` → 415 ProblemDetail
- [x] Todas las respuestas de error con `application/problem+json` (salvo el cuerpo custom de stock insuficiente — ver desviaciones)
- [x] Catch-all loguea la excepción; el body 500 sigue genérico
- [x] `detail` de los errores de negocio sin cambios (13 tests existentes verdes)
- [x] `docs/api-contracts/API_CONTRACT.md` **sección 3 (inventory-service)** actualizada: `/reserve` con
      `remainingStock` + `unitPrice` + `productName`, `/release` con `orderNumber` + `releases[]`, `/stock` con
      `stock` + `reservedStock`, y la tabla de errores con `application/problem+json` (400/404/405/409/415)
- [x] La **sección 2 (order-service)** queda **sin tocar** y marcada como no revisada en esta ODD
- [x] `mvn verify -pl inventory-service` pasa
- [x] `mvn spotless:check -pl inventory-service` pasa

## Tests

| Alcance | Antes | Después |
|---|---:|---:|
| `inventory-service` (unit + slice) | 36 | **41** (22 controller + 13 service + 6 JPA slice), 0 failures, 0 errors, 0 skipped |
| `inventory-service` IT con Docker | 4 skipped | **5 ejecutados** contra Postgres 15 real, 0 failures, 0 errors, 0 skipped |
| Reactor completo (JDK 21) | 106 | **111** (gateway 7 · order 63 · inventory 41), 0 failures, 0 errors, 0 skipped |

Comandos ejecutados (JDK 21, Docker **disponible** en esta sesión):

```bash
cd src
JAVA_HOME=/usr/lib/jvm/java-21-openjdk mvn -pl inventory-service verify      # BUILD SUCCESS
JAVA_HOME=/usr/lib/jvm/java-21-openjdk mvn -pl inventory-service,order-service,api-gateway test
JAVA_HOME=/usr/lib/jvm/java-21-openjdk mvn -pl inventory-service spotless:check
```

### Evidencia de la corrección (respuestas reales tras el cambio)

| Petición | Antes (HEAD previo) | Después | Correcto |
|---|---|---|---|
| `PUT /api/inventory/reserve` | 500 | **405** `application/problem+json` | 405 ✅ |
| `POST /api/inventory/reserve` con `Content-Type: text/plain` | 500 | **415** `application/problem+json` | 415 ✅ |
| `POST /api/inventory/release` con `Content-Type: text/plain` | 500 | **415** `application/problem+json` | 415 ✅ |
| `GET /api/inventory/a/b` (sin mapping) | 500 | **404** `application/problem+json` | 404 ✅ |
| `IllegalStateException("boom")` | 500 sin log | 500 genérico + `ERROR` con stack trace | ✅ |

`detail` reales emitidos por Spring 6.2 (fijados en los tests, no supuestos):

- 405 → `Method 'PUT' is not supported.`
- 415 → `Content-Type 'text/plain;charset=UTF-8' is not supported.`
- 404 sin mapping → detalle por defecto de Spring (`NoResourceFoundException`).

### Batería de mutación (el test muerde)

Se revirtió el handler a su versión previa (`git show HEAD~…:ApiExceptionHandler.java`) manteniendo los tests
nuevos: **5 tests mueren**, exactamente los que cubren los defectos que B4-07 corrige.

```
putOnReserveReturns405ProblemDetail                → 500 esperado 405
unsupportedMediaTypeOnReserveReturns415ProblemDetail→ 500 esperado 415
unsupportedMediaTypeOnReleaseReturns415ProblemDetail→ 500 esperado 415
unmappedPathReturns404ProblemDetail                → 500 esperado 404
unexpectedFailureLogsTheException                  → el catch-all no loguea
```

Handler restaurado después de la prueba (`cp` sobre el archivo) y suite de nuevo en verde (22/22).

## Desviaciones / TODO / gaps

- **S4 con una excepción explícita:** el rechazo de stock insuficiente (`400`) **no** es
  `application/problem+json`; conserva su cuerpo JSON plano
  (`{productCode, available:false, remainingStock, message}`). Motivo: `S2` exige no tocar ese cuerpo y
  `order-service` lo deserializa en `InventoryReserveResponse`
  (`LoadBalancedInventoryStockClient.extractBadRequestBody`); convertirlo en `ProblemDetail` rompería el
  contrato entre servicios. Queda documentado en `API_CONTRACT.md` §3.5 y es el único error con esa forma.
- **`/stock` no expone `remainingStock`:** la entidad `InventoryProduct` serializa `stock` + `reservedStock`
  (además de `id`, `name`, `description`, `unitPrice`, `version`, `createdAt`, `updatedAt`). `remainingStock`
  existe **solo** en la respuesta de `/reserve`. El contrato documenta la forma real en lugar de la que pedía
  la task E.
- La sección 2 del contrato (`order-service`) **no** se tocó: sigue sin revisarse en esta ODD.
- El `detail` de 405/415 depende del texto literal de Spring; si se actualiza la versión de Spring hay que
  reajustar esos dos asserts.

## Commits

```
(pendiente)
```
