# Apply Progress — B4-09: Cobertura de validación del DTO de reserva (cierre de gaps de B4-01)

Rama: `brydyan/feature/inventory-reserve-hardening`
Base: `0cfe06b`
ODD: `odd/tasks/B4-09-inventory-dto-validation-coverage.md`

## Tasks

- [ ] A — Test `quantity` ausente → 400 `"quantity es requerido"` (`<commit>`)
- [ ] B — Test `quantity: null` → 400 `"quantity es requerido"` (`<commit>`)
- [ ] C — Test `productCode` ausente → 400 `"productCode no puede estar vacío"` (`<commit>`)
- [ ] D — Test sin `orderNumber` → 200, `reserveStock("PROD-001", 5, null)` (`<commit>`)
- [ ] E — `verifyNoInteractions(reservationService)` en los 3 casos de validación rechazada (`<commit>`)
- [ ] F — Trazabilidad de B4-01 verificada (ya aplicada en el commit de esta ODD) (`<commit>`)
- [ ] G — `mvn -pl inventory-service verify` + `spotless:check` en verde (`<commit>`)

## Definition of Done

- [ ] Test `quantity` ausente → 400 ProblemDetail `"quantity es requerido"`
- [ ] Test `quantity: null` → 400 ProblemDetail `"quantity es requerido"`
- [ ] Test `productCode` ausente → 400 ProblemDetail `"productCode no puede estar vacío"`
- [ ] Test sin `orderNumber` → 200 y `reserveStock("PROD-001", 5, null)`
- [ ] Los 13 tests actuales de `InventoryControllerTest` siguen verdes sin modificarlos
- [ ] `verifyNoInteractions(reservationService)` en los 3 casos de validación rechazada
- [ ] Trazabilidad de B4-01 correcta (`14 → 21`, DoD marcado)
- [ ] `mvn -pl inventory-service verify` pasa
- [ ] `mvn -pl inventory-service spotless:check` pasa

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
