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

- [x] `InventoryReservationService` creado con UPDATE atómico
- [x] `reserveAtomically()` retorna `int` (filas actualizadas)
- [x] Controller delega en servicio
- [x] Tests concurrentes pasan (20 threads → 20 éxitos, stock final=0)
- [x] 0 `ObjectOptimisticLockingFailureException` en logs
- [x] `mvn verify -pl inventory-service` pasa

> **DoD marcado 2026-11-24** durante la verificación `03-verify-odd` de B4-02 (resultado inicial: **PASS con WARN**).
> **WARN cerrado 2026-11-24 11:05** → veredicto final **PASS**: los 3 huecos que dejó la verificación (caché stale
> tras `@Modifying`, invariante de atomicidad del `remainingStock` sin documentar, suite solo-mock) los cerró
> **B4-10**, verificado **PASS** el 2026-11-24: el flag `clearAutomatically` está puesto y hay un test de slice JPA
> que **muere** si se quita; el invariante está escrito en el servicio; y las queries se ejecutan ya contra una
> base real en CI (H2), no solo contra mocks.
> Salvedad honesta: el test de concurrencia de la suite es **mock-based** (`AtomicInteger` + CAS), no ejecuta
> PostgreSQL; la prueba real contra PostgreSQL 15 se hizo **a mano** en la verificación (20 hilos → 20×200 uds,
> stock final 0, `reserved_stock` 20, `version` 20, 0 `ObjectOptimisticLockingFailureException`) pero **no quedó
> en el repo**. La cobertura permanente es de **B4-06** (Testcontainers).
> Los 3 huecos que quedaron en la verificación (caché stale tras `@Modifying`, invariante de atomicidad del
> `remainingStock` sin documentar, suite solo-mock) quedan trackeados en **B4-10**.

---

## Log

**L1:** 2026-11-23 23:35 — B4-02 creado. UPDATE atómico. Resuelve INV-03.
