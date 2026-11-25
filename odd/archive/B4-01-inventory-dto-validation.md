# ODD — B4-01: DTOs Tipados + Validación (INV-02)

**Rama:** `brydyan/feature/inventory-reserve-hardening`  
**Depende de:** Nada (primera)  
**Bloquea:** B4-02, B4-03, B4-04, B4-05  
**Responsable:** Andy

---

## Spec

### S1 — ReserveStockRequest tipado y validado

**Cambio:** De `Map<String, Object>` a DTO tipado.

**Input (POST /api/inventory/reserve):**
```json
{
  "productCode": "PROD-001",
  "quantity": 5,
  "orderNumber": "ORD-20250101-001"
}
```

**DTO:**
```java
public record ReserveStockRequest(
    @NotBlank(message = "productCode no puede estar vacío")
    String productCode,
    
    @NotNull(message = "quantity es requerido")
    @Positive(message = "quantity debe ser > 0")
    Integer quantity,
    
    @Nullable
    String orderNumber  // Para idempotencia (B5)
) {}
```

**Validación (Framework):**
- Jackson: rechaza type mismatch (`quantity: "abc"`, `quantity: 2.0`) → 400
- `@NotBlank`, `@Positive` → 400 ProblemDetail si fallan

**Hecho cuando:**
- `POST /reserve quantity: "abc"` → 400 ProblemDetail (no 500)
- `POST /reserve quantity: 2.0` → 400 ProblemDetail (no 500)
- `POST /reserve quantity: -1` → 400 ProblemDetail (no 500)
- `POST /reserve productCode: ""` → 400 ProblemDetail (no 400)

---

## Tasks

| ID | Descripción | Archivo |
|----|---|---|
| A | Agregar `spring-boot-starter-validation` a `pom.xml` | `pom.xml` |
| B | Crear `ReserveStockRequest` record con anotaciones | `dto/ReserveStockRequest.java` |
| C | Crear excepciones de dominio: `ProductNotFoundException`, `StockUnavailableException` | `exception/ProductNotFoundException.java`, `exception/StockUnavailableException.java` |
| D | Actualizar `InventoryController#reserveStock` para usar DTO | `controller/InventoryController.java` |

---

## Definition of Done

- [x] `pom.xml` incluye `spring-boot-starter-validation`
- [x] DTO creado con `@NotBlank`, `@NotNull`, `@Positive`
- [x] Excepciones de dominio creadas
- [x] Controller usa DTO, no `Map<String, Object>`
- [x] Tests unitarios pasan: validación type mismatch → 400
- [x] `mvn spotless:apply && mvn verify -pl inventory-service` pasa

> **DoD marcado 2026-11-24** durante la verificación `03-verify-odd` de B4-01 (resultado inicial: **PASS con WARN**).
> **WARN cerrado 2026-11-24 11:05** → veredicto final **PASS**: (b) los números de test se corrigieron el mismo día
> (ver nota en el apply-progress) y (a) la falta de test para `@NotNull` quedó cubierta por **B4-09** (verificado
> **PASS**): al quitar el `@NotNull` de `quantity`, la suite actual **falla** (2 errores en
> `InventoryControllerTest`), o sea que el hueco ya no existe.
> El WARN original no venía de esta lista: venía de (a) falta de test para `@NotNull` y (b) números de test incorrectos en
> `B4-01-apply-progress.md`. Ambos quedan trackeados en **B4-09**.

---

## Log

**L1:** 2026-11-23 23:30 — B4-01 creado. DTOs tipados + validación. Resuelve INV-02.
