# ODD — B4-04: Respuesta con Precio y Nombre (INV-05)

**Rama:** `brydyan/feature/inventory-reserve-hardening`  
**Depende de:** B4-01 (DTOs creados)  
**Bloquea:** —  
**Responsable:** Andy

---

## Spec

### S1 — ReservationResponse con unitPrice + productName

**Cambio:** Extender respuesta 200 OK.

**Output (éxito 200):**
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

**DTO:**
```java
public record ReservationResponse(
    String productCode,
    boolean available,
    Integer remainingStock,
    BigDecimal unitPrice,      // ← NEW
    String productName,         // ← NEW
    String message
) {}
```

**Contrato (compatible):** Clientes antiguos ignoran campos nuevos.

**Hecho cuando:** `POST /reserve PROD-001 qty=5` devuelve `unitPrice: 1899.99` y `productName: "Laptop Dell XPS 15"`.

---

## Tasks

| ID | Descripción | Archivo |
|----|---|---|
| A | Crear `ReservationResponse` record con nuevos campos | `dto/ReservationResponse.java` |
| B | Actualizar `InventoryReservationService` para devolver `ReservationResponse` | `service/InventoryReservationService.java` |
| C | Actualizar `InventoryController` para usar nuevo DTO | `controller/InventoryController.java` |
| D | Tests: verificar campos `unitPrice` y `productName` en respuesta | `src/test/java/...` |

---

## Definition of Done

- [x] `ReservationResponse` creado con `unitPrice` y `productName`
- [x] Servicio retorna `ReservationResponse` con valores del producto
- [x] Controller usa nuevo DTO
- [x] Tests verifican que respuesta incluye precio y nombre
- [x] `mvn verify -pl inventory-service` pasa

> **DoD marcado 2026-11-24** durante la verificación `03-verify-odd` de B4-04 (resultado: **PASS**).
> Evidencia completa en `odd/archive/B4-04-apply-progress.md`.

---

## Log

**L2:** 2026-11-24 10:55 — Verificación `03-verify-odd`: **PASS**. Las 3 mutaciones (productName vacío, unitPrice
nulo, `remainingStock` desfasado) mueren con la suite actual; la respuesta HTTP real incluye `unitPrice` y
`productName`. ODD verificada.

**L1:** 2026-11-23 23:40 — B4-04 creado. Respuesta con precio. Resuelve INV-05.
