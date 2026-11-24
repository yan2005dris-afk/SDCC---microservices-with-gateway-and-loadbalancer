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

- [ ] `ApiExceptionHandler` creado con 5 handlers
- [ ] Producto no existe devuelve 404 (no 400)
- [ ] Validación devuelve ProblemDetail
- [ ] Stock insuficiente devuelve 400 custom `available: false`
- [ ] Tests pasan
- [ ] `mvn verify -pl inventory-service` pasa

---

## Log

**L1:** 2026-11-23 23:38 — B4-03 creado. ProblemDetail + 404. Resuelve INV-04.
