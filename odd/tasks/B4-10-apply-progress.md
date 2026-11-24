# Apply Progress — B4-10: Durabilidad de la reserva atómica (cierre de gaps de B4-02)

Rama: `brydyan/feature/inventory-reserve-hardening`
Base: `<commit actual de la rama>`
ODD: `odd/tasks/B4-10-inventory-atomic-reserve-durability.md`

> ⚠️ Aplicar **después** de B4-08 (el otro agente reescribe `InventoryReservationService`).

## Tasks

- [ ] A — `@Modifying(clearAutomatically = true, flushAutomatically = true)` en los 2 queries (`<commit>`)
- [ ] B — Test de regresión de slice JPA: la relectura ve el stock nuevo (`<commit>`)
- [ ] C — Test de slice JPA: `qty > stock` → 0 filas, nada cambia (`<commit>`)
- [ ] D — Test de slice JPA: 20 reservas concurrentes → stock 0, reserved 20, version 20 (`<commit>`)
- [ ] E — Comentario del invariante del `remainingStock` en el servicio (`<commit>`)
- [ ] F — Dependencia `h2` scope `test` en `inventory-service/pom.xml` (`<commit>`)
- [ ] G — DoD de la spec archivada de B4-02 marcado con nota (`<commit>`)
- [ ] H — `mvn -pl inventory-service verify` + `spotless:check` en verde (`<commit>`)

## Definition of Done

- [ ] `@Modifying` con `clearAutomatically = true` y `flushAutomatically = true` en los 2 queries
- [ ] Test JPA: tras el `UPDATE` masivo, la relectura ve el `stock` nuevo (falla si se quita el flag)
- [ ] Test JPA: `qty > stock` → 0 filas afectadas y la fila queda intacta
- [ ] Test JPA: 20 reservas concurrentes → stock 0, `reserved_stock` 20, `version` 20, 0 excepciones
- [ ] El invariante del `remainingStock` está escrito en el código
- [ ] `h2` en scope `test` en `inventory-service/pom.xml`
- [ ] DoD de la spec archivada de B4-02 marcado con nota
- [ ] Los 22 tests actuales siguen verdes sin modificarlos
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
