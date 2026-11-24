# Apply Progress — B4-04: Respuesta con Precio y Nombre (INV-05)

Rama: `brydyan/feature/inventory-reserve-hardening`
Base: `6c6bfd1`

## Tasks

- [x] A — Crear `ReservationResponse` record (`0452b2c`)
- [x] B — Servicio devuelve `ReservationResponse` (`5c3b148`)
- [x] C — Controller usa el nuevo DTO (`5c3b148`)
- [x] D — Tests verifican `unitPrice` y `productName` (`306920f`)

## Definition of Done

- [x] `ReservationResponse` creado con `unitPrice` y `productName`
- [x] Servicio retorna `ReservationResponse` con valores del producto
- [x] Controller usa nuevo DTO
- [x] Tests verifican que la respuesta incluye precio y nombre
- [x] `mvn verify -pl inventory-service` pasa

## Tests

| Alcance | Antes | Después |
|---|---|---|
| `inventory-service` | 14 | 14 (0 failures, 0 errors) |
| Reactor completo (JDK 21) | 28 | 28 (0 failures, 0 errors) |

No se agregaron nuevos métodos de test; se reforzaron los existentes:

- `InventoryReservationServiceTest.successfulReserveReturnsRemainingStockWithoutEntitySave` ahora valida los 6 campos del DTO, incluyendo `unitPrice=1899.99` y `productName=Laptop Dell XPS 15`.
- `InventoryControllerTest.validRequestDelegatesToService` ahora valida `productCode`, `available`, `remainingStock`, `unitPrice`, `productName` y `message`.
- `InventoryReservationServiceTest` usa `Future<ReservationResponse>` en el test de concurrencia.

No hubo RED observable por cambio de firma: el cambio de tipo de retorno rompía compilación hasta propagarlo al controller y a los mocks.

## Respuesta 200

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

`X-Instance-Id` se mantiene igual. Contrato compatible: clientes antiguos ignoran campos nuevos.

## Desviaciones / TODO / gaps

- Tasks B y C se commitearon juntos (`5c3b148`). Cambiar el tipo de retorno del servicio rompe la compilación del controller; separarlos habría dejado un commit intermedio en rojo, violando la gate de tests.
- `ReservationResponse.productName` se mapea desde `InventoryProduct.name`; la entidad no tiene campo `productName`.
- El servicio sigue haciendo `findByProductCode` después de la UPDATE condicional para construir la respuesta. No hay read-modify-write.
- JDK 25 rompe Spotless 2.44.3 y Mockito. Verificación ejecutada con `JAVA_HOME=/usr/lib/jvm/java-21-openjdk`.

## Commits

```
306920f test(inventory): assert price and product name in reserve response
5c3b148 feat(inventory): return enriched reservation response
0452b2c feat(inventory): add ReservationResponse DTO with price and name
```

---

## Verificación (`03-verify-odd`, 2026-11-24 10:55) — PASS

Verificación con **ejecución real** contra `HEAD` (`adbec0c`).

### Task completion

| Task | Estado | Evidencia |
|---|---|---|
| A — `ReservationResponse` con `unitPrice` y `productName` | ✅ | `dto/ReservationResponse.java` |
| B — el servicio devuelve valores del producto | ✅ | `InventoryReservationService#reservationResponse` usa `product.getUnitPrice()` y `product.getName()` |
| C — el controller usa el DTO nuevo | ✅ | `InventoryController#reserveStock` devuelve `reservationService.reserveStock(...)` |
| D — tests que verifican precio y nombre | ✅ | `InventoryReservationServiceTest` + respuesta HTTP real |

### Evidencia en HTTP real (probe con H2, esquema replicado)

```
H1 primera status=200 body={"productCode":"PROD-A","available":true,"remainingStock":45,
   "unitPrice":10.00,"productName":"Producto A","message":"Reserva confirmada"}
```

Los dos campos nuevos viajan en el body con el nombre y el tipo que fija la spec (`unitPrice` numérico,
`productName` texto).

### Cobertura de mutaciones (3/3 mueren)

| # | Mutación | Resultado |
|---|---|---|
| B4-04-M1 | `productName` vacío en la respuesta | ✅ MUERE |
| B4-04-M2 | `unitPrice` nulo en la respuesta | ✅ MUERE |
| B4-04-M3 | `remainingStock` desfasado (+1) | ✅ MUERE |

### Regresión de tests

`inventory-service` 32 unit ✅ · reactor 102 ✅ · `mvn verify` BUILD SUCCESS ✅ · `spotless:check` exit 0 ✅.

### Calidad de commits

Commits convencionales, autor `Andy Alejandro <andy.alejandrovera4290@upse.edu.ec>`, sin tags de IA.

### Veredicto

**✅ PASS.** DoD completo, sin huecos, sin WARN. **Permanece en `odd/archive/`.**
