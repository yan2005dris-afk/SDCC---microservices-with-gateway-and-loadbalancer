# ODD — B4-05: Movimientos de Stock + Endpoint /release (INV-06)

**Rama:** `brydyan/feature/inventory-reserve-hardening`  
**Depende de:** B4-02 (Reserva atómica)  
**Bloquea:** B4-06 (Tests)  
**Responsable:** Andy

---

## Spec

### S1 — StockMovement entity

**Nueva entidad:**
```java
@Entity
@Table(name = "stock_movement_history")
public class StockMovement {
    @Id
    @GeneratedValue
    private Long id;
    
    @Column(name = "product_code")
    private String productCode;
    
    @Column(name = "movement_type")
    @Enumerated(EnumType.STRING)
    private MovementType type;  // RESERVATION, RELEASE, RESTOCK
    
    private Integer quantity;
    
    @Column(name = "reference_order_number")
    private String referenceOrderNumber;
    
    @Column(name = "created_at")
    private OffsetDateTime createdAt = OffsetDateTime.now();
}

public enum MovementType {
    RESERVATION,
    RELEASE,
    RESTOCK
}
```

**Índice único en SQL (02-init-schemas.sql):**
```sql
CREATE UNIQUE INDEX uq_stock_movement_ref_type 
ON stock_movement_history(reference_order_number, movement_type)
WHERE reference_order_number IS NOT NULL;
```

**Idempotencia:**
- Dos `POST /reserve ORD-001` del mismo producto → segunda retorna 200 (ya reservado), stock solo decrementó una vez.

### S2 — POST /api/inventory/release (nuevo endpoint)

**Input:**
```json
{
  "orderNumber": "ORD-20250101-001"
}
```

**Output (éxito 200):**
```json
{
  "productCode": "PROD-001",
  "quantity": 5,
  "message": "Stock liberado exitosamente"
}
```

**Comportamiento:**
1. Busca `StockMovement(type=RESERVATION, reference=orderNumber)`
2. Si no existe → 404
3. Si existe y aún no hay RELEASE → 
   - Decrementar `reservedStock`, incrementar `stock`
   - Insertar `StockMovement(type=RELEASE)`
4. Si existe y ya hay RELEASE → 200 (idempotente)

**Hecho cuando:**
- `POST /reserve ORD-001 PROD-001 qty=5` → stock: 50→45, reserved: 0→5, movimiento RESERVATION
- `POST /release ORD-001` → stock: 45→50, reserved: 5→0, movimiento RELEASE
- `POST /release ORD-001` (segunda vez) → 200 (idempotente)

---

## Tasks

| ID | Descripción | Archivo |
|----|---|---|
| A | Crear `StockMovement` entity + `MovementType` enum | `model/StockMovement.java` |
| B | Crear `StockMovementRepository` | `repository/StockMovementRepository.java` |
| C | Actualizar SQL: agregar índice único en `02-init-schemas.sql` | `docker/postgres/02-init-schemas.sql` |
| D | Agregar inserción de `StockMovement` al `InventoryReservationService` (RESERVATION) | `service/InventoryReservationService.java` |
| E | Crear `ReleaseStockRequest` DTO | `dto/ReleaseStockRequest.java` |
| F | Crear `POST /api/inventory/release` en controller | `controller/InventoryController.java` |
| G | Implementar lógica idempotente de `/release` en servicio | `service/InventoryReservationService.java` |
| H | Tests: idempotencia, /release idempotente | `src/test/java/...` |

---

## Definition of Done

- [ ] `StockMovement` entity creada con `reference_order_number`, `movement_type`
- [ ] Índice único en SQL aplicado
- [ ] `InventoryReservationService` inserta `StockMovement(RESERVATION)` en la misma transacción
- [ ] `POST /release` creado e idempotente
- [ ] Dos `/reserve` del mismo `orderNumber` → segundo retorna 200, stock solo decrementó una vez
- [ ] Dos `/release` del mismo `orderNumber` → ambas retornan 200
- [ ] Tests pasan
- [ ] `mvn verify -pl inventory-service` pasa

---

## Log

**L1:** 2026-11-23 23:43 — B4-05 creado. StockMovement + /release. Resuelve INV-06.
