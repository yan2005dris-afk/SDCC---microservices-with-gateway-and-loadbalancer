# ODD — B6-02: Reposición de Stock (INV-07)

**Rama:** `brydyan/feature/inventory-reserve-hardening`  
**Depende de:** B4-05 (StockMovement entity, service)  
**Bloquea:** —  
**Responsable:** Andy

---

## Spec

### S1 — PUT /api/inventory/{productCode}/stock (reposición)

**Endpoint:**
```
PUT /api/inventory/{productCode}/stock
```

**Input:**
```json
{
  "quantity": 25
}
```

**DTO:**
```java
public record RestockRequest(
    @NotNull(message = "quantity requerido")
    @Positive(message = "quantity debe ser > 0")
    Integer quantity
) {}
```

**Response (200):**
```json
{
  "productCode": "PROD-001",
  "quantity": 25,
  "stock": 75,
  "message": "Stock reabastecido exitosamente"
}
```

**Comportamiento:**
- UPDATE atómico: `stock = stock + quantity`
- Insertar `StockMovement(type=RESTOCK, quantity, productCode)`
- En la misma transacción

**Error (404):**
```json
{
  "type": "about:blank",
  "title": "Not Found",
  "status": 404,
  "detail": "Producto PROD-999 no encontrado",
  "instance": "/api/inventory/PROD-001/stock"
}
```

**Hecho cuando:**
- `PUT /api/inventory/PROD-001/stock {quantity: 25}` → 200, stock incrementó en 25
- Movimiento RESTOCK creado
- `PUT /api/inventory/PROD-999/stock {quantity: 10}` → 404

---

## Tasks

| ID | Descripción | Archivo |
|----|---|---|
| A | Crear `RestockRequest` DTO | `dto/RestockRequest.java` |
| B | Crear `InventoryRestockService#restockProduct(code, qty)` | `service/InventoryRestockService.java` |
| C | Agregar `restockAtomically()` a repository (UPDATE + INSERT movimiento) | `repository/InventoryProductRepository.java` |
| D | Crear `PUT /api/inventory/{productCode}/stock` en controller | `controller/InventoryController.java` |
| E | Tests: reposición correcta, 404, movimiento RESTOCK creado | `src/test/java/...` |

---

## Definition of Done

- [ ] `PUT /api/inventory/{code}/stock` devuelve 200 con stock actualizado
- [ ] Stock incrementó correctamente
- [ ] Movimiento RESTOCK insertado
- [ ] 404 si producto no existe
- [ ] Tests: reposición normal, 404, contenido
- [ ] `mvn verify -pl inventory-service` pasa

---

## Log

**L1:** 2026-11-24 00:16 — B6-02 creado. Reposición atómica + movimiento RESTOCK. INV-07 parte 2.
