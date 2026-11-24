# Apply Progress — B4-07: Corrección de semántica HTTP en el manejo de errores (INV-04 rework)

Rama: `brydyan/feature/inventory-reserve-hardening`
Base: `0b3aa12`
ODD: `odd/tasks/B4-07-inventory-error-handling-fix.md`

## Tasks

- [ ] A — `ApiExceptionHandler extends ResponseEntityExceptionHandler` (`<commit>`)
- [ ] B — Sobrescribir `handleMethodArgumentNotValid`, `handleHttpMessageNotReadable`, `handleExceptionInternal` (`<commit>`)
- [ ] C — Handlers de dominio intactos + catch-all con `log.error` (`<commit>`)
- [ ] D — Tests 405 / 415 / 404 sin mapping / 415 en `/release` + content type (`<commit>`)
- [ ] E — `docs/api-contracts/API_CONTRACT.md` actualizado (`<commit>`)
- [ ] F — `mvn verify -pl inventory-service` + `spotless:check` en verde (`<commit>`)

## Definition of Done

- [ ] `ApiExceptionHandler` hereda de `ResponseEntityExceptionHandler`
- [ ] `PUT /api/inventory/reserve` → 405 ProblemDetail
- [ ] `POST /reserve` con `text/plain` → 415 ProblemDetail
- [ ] Ruta sin mapping → 404 ProblemDetail
- [ ] `POST /release` con `text/plain` → 415 ProblemDetail
- [ ] Todas las respuestas de error con `application/problem+json`
- [ ] Catch-all loguea la excepción; el body 500 sigue genérico
- [ ] `detail` de los errores de negocio sin cambios (13 tests existentes verdes)
- [ ] `docs/api-contracts/API_CONTRACT.md` actualizado
- [ ] `mvn verify -pl inventory-service` pasa
- [ ] `mvn spotless:check -pl inventory-service` pasa

## Tests

| Alcance | Antes | Después |
|---|---|---|
| `inventory-service` | 22 | ? (0 failures, 0 errors) |
| Reactor completo (JDK 21) | 92 | ? (0 failures, 0 errors) |

## Desviaciones / TODO / gaps

_(pendiente)_

## Commits

```
(pendiente)
```
