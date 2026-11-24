# ODD — B4-02: Reserva Atómica (INV-03)

**Rama:** `brydyan/feature/inventory-reserve-hardening`  
**Depende de:** B4-01 (DTOs creados)  
**Bloquea:** B4-05, B4-06  
**Responsable:** Andy

---

## Spec

### S1 — UPDATE atómico sin @Version conflictos

**Problema actual:**
- Thread A: lee (v=0) → modifica → guarda (v=0→1) ✓
- Thread B: lee (v=0) → modifica → guarda (v=0→1) ✗ ObjectOptimisticLockingFailureException → 500

**Solución:** UPDATE SQL con condición, no read-modify-write.

```java
@Modifying
@Query("""
    UPDATE InventoryProduct p
       SET p.stock = p.stock - :qty,
           p.reservedStock = p.reservedStock + :qty,
           p.version = p.version + 1,
           p.updatedAt = CURRENT_TIMESTAMP
     WHERE p.productCode = :code AND p.stock >= :qty
    """)
int reserveAtomically(String code, int qty);
```

**Comportamiento:**
- Retorna 1 si stock >= qty (stock decrementado)
- Retorna 0 si stock < qty (nada cambia)

**Lógica del servicio:**
```java
int updated = repository.reserveAtomically(code, qty);
if (updated == 0) {
    // Leer para distinguir "stock insuficiente" vs "producto no existe"
    Optional<InventoryProduct> product = repository.findByProductCode(code);
    if (product.isEmpty()) {
        throw new ProductNotFoundException(code);
    } else {
        throw new StockUnavailableException(code, product.get().getStock());
    }
}
```

**Hecho cuando:** 20 hilos concurrentes reservan 1 unidad cada uno del mismo producto (stock=20):
- Exactamente 20 éxitos (retorna 200 OK)
- 0 errores 500
- Stock final = 0
- 0 `ObjectOptimisticLockingFailureException`

---

## Tasks

| ID | Descripción | Archivo |
|----|---|---|
| A | Crear `InventoryReservationService` con `@Transactional` | `service/InventoryReservationService.java` |
| B | Agregar `reserveAtomically()` a `InventoryProductRepository` | `repository/InventoryProductRepository.java` |
| C | Refactor `InventoryController#reserveStock` para delegar a servicio | `controller/InventoryController.java` |
| D | Tests: unitario de servicio + integración con 20 threads | `src/test/java/...` |

---

## Definition of Done

- [ ] `InventoryReservationService` creado con UPDATE atómico
- [ ] `reserveAtomically()` retorna `int` (filas actualizadas)
- [ ] Controller delega en servicio
- [ ] Tests concurrentes pasan (20 threads → 20 éxitos, stock final=0)
- [ ] 0 `ObjectOptimisticLockingFailureException` en logs
- [ ] `mvn verify -pl inventory-service` pasa

---

## Log

**L1:** 2026-11-23 23:35 — B4-02 creado. UPDATE atómico. Resuelve INV-03.
