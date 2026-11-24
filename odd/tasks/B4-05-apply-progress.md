# Apply Progress — B4-05: Movimientos de Stock + Endpoint /release (INV-06)

Rama: `brydyan/feature/inventory-reserve-hardening`
Base: `306920f`

## Tasks

- [x] A — `StockMovement` entity + `MovementType` enum (`6103937`)
- [x] B — `StockMovementRepository` (`41fae67`)
- [x] C — Índice único en `02-init-schemas.sql` (`a2edd99`)
- [x] D — `InventoryReservationService` inserta RESERVATION + idempotencia de reserve (`666761e`)
- [x] E — `ReleaseStockRequest` DTO (`aef58e5`)
- [x] F — `POST /api/inventory/release` (`f176411`)
- [x] G — Lógica idempotente de release en servicio (`f176411`)
- [x] H — Tests de idempotencia y release (`23e4410`)

## Definition of Done

- [x] `StockMovement` entity creada con `reference_order_number`, `movement_type`
- [x] Índice único en SQL aplicado
- [x] `InventoryReservationService` inserta `StockMovement(RESERVATION)` en la misma transacción
- [x] `POST /release` creado e idempotente
- [x] Dos `/reserve` del mismo `orderNumber` → segundo no decrementa (test unitario)
- [x] Dos `/release` del mismo `orderNumber` → segundo no restaura (test unitario)
- [x] Tests pasan
- [x] `mvn verify -pl inventory-service` pasa

## Tests

| Alcance | Antes | Después |
|---|---|---|
| `inventory-service` | 14 | 22 (0 failures, 0 errors) |
| Reactor completo (JDK 21) | 28 | 36 (0 failures, 0 errors) |

`InventoryControllerTest`: 10 → 13. Agregados: release 200, release con `orderNumber` vacío → 400 ProblemDetail, release sin reserva → 404 ProblemDetail.

`InventoryReservationServiceTest`: 4 → 9. Agregados: movimiento RESERVATION con orderNumber, reserve repetido sin segundo decremento, release con movimiento RELEASE y stock restaurado, release repetido sin segunda restauración, release sin reserva → excepción.

No hubo RED observable por cambio de firma: el cambio de `reserveStock` a 3 argumentos rompía compilación hasta propagarse a controller y mocks.

## Contrato

Reserve con `orderNumber`:

```json
{
  "productCode": "PROD-001",
  "available": true,
  "remainingStock": 45,
  "unitPrice": 1899.99,
  "productName": "Laptop Dell XPS 15",
  "message": "Reserva confirmada"
}
```

Release:

```json
{
  "productCode": "PROD-001",
  "quantity": 5,
  "message": "Stock liberado exitosamente"
}
```

Ambos mantienen el header `X-Instance-Id`.

## Desviaciones / TODO / gaps

- Tasks F y G comparten commit (`f176411`): el endpoint no compila sin el método de servicio. Separarlos habría dejado un commit intermedio en rojo.
- Se agregaron tres elementos no listados en la tabla de tasks pero necesarios para el contrato:
  - `ReservationNotFoundException` + handler 404 en `ApiExceptionHandler` — sin él el catch-all de B4-03 devolvía 500 para releases sin reserva.
  - `InventoryProductRepository.releaseAtomically(...)` — evita read-modify-write en release.
  - `InventoryReservationService.reserveStock` pasó de 2 a 3 argumentos para recibir `orderNumber` del DTO existente.
- Idempotencia probada en secuencia (mismo hilo). Dos peticiones concurrentes con el mismo `orderNumber` pueden pasar la comprobación previa; el índice único evita el doble movimiento, pero la segunda petición podría terminar en 500 por `DataIntegrityViolationException` en lugar de 200. B4-06 debe cubrir este caso con PostgreSQL real y, si falla, gestionarse en un task posterior.
- El índice único solo se aplica al inicializar una BD nueva con `02-init-schemas.sql`.Una BD ya creada necesita aplicar el índice manualmente o mediante migración.
- JDK 25 rompe Spotless 2.44.3 y Mockito. Verificación ejecutada con `JAVA_HOME=/usr/lib/jvm/java-21-openjdk`.

## Commits

```
23e4410 test(inventory): cover reserve and release idempotency
f176411 feat(inventory): add idempotent stock release endpoint
aef58e5 feat(inventory): add release stock request DTO
666761e feat(inventory): record RESERVATION movements with idempotent reserve
a2edd99 feat(db): add unique index for stock movement idempotency
41fae67 feat(inventory): add stock movement repository
6103937 feat(inventory): add stock movement model
```
