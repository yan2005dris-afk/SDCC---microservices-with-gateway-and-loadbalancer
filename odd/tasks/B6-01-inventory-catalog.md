# ODD — B6-01: Catálogo Paginado (INV-07)

**Rama:** `brydyan/feature/inventory-reserve-hardening`  
**Depende de:** B4-01, B4-04 (ProductResponse DTO creado)  
**Bloquea:** —  
**Responsable:** Andy

---

## Spec

### S1 — GET /api/inventory paginado

**Endpoint:**
```
GET /api/inventory?page=0&size=20
```

**Query params:**
- `page`: página (default 0)
- `size`: items por página (max 100, default 20)

**Response (200):**
```json
{
  "content": [
    {
      "productCode": "PROD-001",
      "name": "Laptop Dell XPS 15",
      "description": "Intel Core i9, 32GB RAM, 1TB SSD",
      "stock": 50,
      "unitPrice": 1899.99
    },
    ...
  ],
  "totalElements": 5,
  "totalPages": 1,
  "currentPage": 0,
  "size": 20
}
```

**DTO:**
```java
public record ProductResponse(
    String productCode,
    String name,
    String description,
    Integer stock,
    BigDecimal unitPrice
) {}
```

**Contrato:** Mismo ProductResponse de B4-04 (reutilizar).

**Hecho cuando:**
- `GET /api/inventory?page=0&size=10` → 200 con Page<ProductResponse>
- `size=150` (> 100) → rechaza o trunca a 100
- Productos seed (PROD-001 a PROD-005) aparecen

---

## Tasks

| ID | Descripción | Archivo |
|----|---|---|
| A | Crear `InventoryController#listProducts(Pageable)` | `controller/InventoryController.java` |
| B | Usar `PagingAndSortingRepository` en repository (si aún no existe) | `repository/InventoryProductRepository.java` |
| C | Validar `size <= 100` en Pageable | `config/WebConfig.java` o validación inline |
| D | Tests: paginación, size max, contenido correcto | `src/test/java/...` |

---

## Definition of Done

- [ ] `GET /api/inventory` devuelve `Page<ProductResponse>`
- [ ] `size` máximo 100
- [ ] Orden: by productCode ASC (o configurable)
- [ ] Tests: page=0 size=20, content correcto, totalElements
- [ ] `mvn verify -pl inventory-service` pasa

---

## Log

**L1:** 2026-11-24 00:15 — B6-01 creado. Catálogo paginado. INV-07 parte 1.
