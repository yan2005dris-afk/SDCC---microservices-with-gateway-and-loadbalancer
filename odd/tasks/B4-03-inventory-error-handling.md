# ODD — B4-03: Errores Unificados con ProblemDetail (INV-04)

**Rama:** `brydyan/feature/inventory-reserve-hardening`  
**Depende de:** B4-01 (DTOs creados)  
**Bloquea:** —  
**Responsable:** Andy

---

## Spec

### S1 — Contrato de errores unificado

| Caso | Código | Respuesta |
|------|--------|-----------|
| Validación (quantity: "abc") | 400 | `ProblemDetail` |
| Producto no existe | **404** (no 400) | `ProblemDetail` |
| Stock insuficiente | 400 | Custom: `{"productCode", "available": false, "remainingStock", "message"}` |
| Error interno (500) | 500 | `ProblemDetail` |

**ProblemDetail (RFC 7807):**
```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "quantity debe ser > 0",
  "instance": "/api/inventory/reserve"
}
```

**Stock insuficiente (compatible con cliente actual):**
```json
{
  "productCode": "PROD-001",
  "available": false,
  "remainingStock": 2,
  "message": "Stock insuficiente. Disponible: 2, solicitado: 5"
}
```

**Hecho cuando:**
- `GET /api/inventory/PROD-999` → 404 ProblemDetail (no 400)
- `POST /reserve quantity: "abc"` → 400 ProblemDetail (no 500)
- `POST /reserve quantity: 0` → 400 ProblemDetail
- `POST /reserve stock insuficiente` → 400 custom `available: false`

---

## Tasks

| ID | Descripción | Archivo |
|----|---|---|
| A | Crear `ApiExceptionHandler` con `@RestControllerAdvice` | `web/ApiExceptionHandler.java` |
| B | Handlers: `MethodArgumentNotValidException` → 400 ProblemDetail | `web/ApiExceptionHandler.java` |
| C | Handler: `ProductNotFoundException` → 404 ProblemDetail | `web/ApiExceptionHandler.java` |
| D | Handler: `StockUnavailableException` → 400 custom | `web/ApiExceptionHandler.java` |
| E | Handler: `Exception` catch-all → 500 ProblemDetail | `web/ApiExceptionHandler.java` |
| F | Tests: validación, 404, stock insuficiente | `src/test/java/...` |

---

## Definition of Done

- [x] `ApiExceptionHandler` creado con 5 handlers
- [x] Producto no existe devuelve 404 (no 400)
- [x] Validación devuelve ProblemDetail
- [x] Stock insuficiente devuelve 400 custom `available: false`
- [x] Tests pasan
- [x] `mvn verify -pl inventory-service` pasa

> ⚠️ **Verificación 2026-11-24 09:40 → FAIL. B4-03 sigue ABIERTA.**
> El DoD propio de esta ODD **sí** está cumplido (el contrato unificado de S1 funciona y tiene 6/6 handlers con
> cobertura de mutación), **pero** el catch-all de la task Emtró las excepciones HTTP estándar de Spring MVC y
> convirtió 405/415/404 en **500** — una **regresión** frente a `eb27734` (pre-B4-03), donde esos códigos
> eran correctos. Además el catch-all no loguea la causa raíz y `docs/api-contracts/API_CONTRACT.md` quedó
> desactualizado.
> **Rework obligatorio:** `odd/tasks/B4-07-inventory-error-handling-fix.md` (aún sin implementar).

---

## Log

**L1:** 2026-11-23 23:38 — B4-03 creado. ProblemDetail + 404. Resuelve INV-04.
