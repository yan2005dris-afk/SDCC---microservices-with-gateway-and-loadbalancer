# Apply Progress — B4-03: Errores Unificados con ProblemDetail (INV-04)

Rama: `brydyan/feature/inventory-reserve-hardening`
Base: `eb27734`

## Tasks

- [x] A — `ApiExceptionHandler` con `@RestControllerAdvice` (`abd9451`)
- [x] B — `MethodArgumentNotValidException` → 400 ProblemDetail (`91dfa66`)
- [x] C — `ProductNotFoundException` → 404 ProblemDetail (`689da56`)
- [x] D — `StockUnavailableException` → 400 custom (`cfd52e8`)
- [x] E — `Exception` catch-all → 500 ProblemDetail (`110d9a5`)
- [x] F — Tests de validación, 404 y stock insuficiente (`6c6bfd1`)

## Definition of Done

- [x] `ApiExceptionHandler` creado con 5 handlers
- [x] Producto no existe devuelve 404 (no 400)
- [x] Validación devuelve ProblemDetail
- [x] Stock insuficiente devuelve 400 custom `available: false`
- [x] Tests pasan
- [x] `mvn verify -pl inventory-service` pasa

## Tests

| Alcance | Antes | Después |
|---|---|---|
| `inventory-service` | 9 | 14 (0 failures, 0 errors) |
| Reactor completo (JDK 21) | 23 | 28 (0 failures, 0 errors) |

`InventoryControllerTest`: 5 → 10. Agregados: `quantity: 0`, GET 404, POST 404, stock insuficiente, catch-all 500. Los tests de validación ahora verifican `type`, `title`, `status`, `detail` e `instance`.

No hubo RED observable: el advice se agregó sobre código ya implementado y los tests refuerzan el contrato.

## Respuestas

- Validación: `ProblemDetail` con `type=about:blank`, `title=Bad Request`, `status=400`, `instance=/api/inventory/reserve`.
- Producto inexistente: `ProblemDetail` 404, `detail=Producto no encontrado: <code>`.
- Stock insuficiente: `{"productCode", "available": false, "remainingStock", "message"}` con status 400.
- Error inesperado: `ProblemDetail` 500, `detail=Error interno del servidor` (sin exponer stack trace).

## Desviaciones / TODO / gaps

- Se agregó un handler para `HttpMessageNotReadableException` → 400 ProblemDetail, no listado en la tabla de tasks. Sin él, el catch-all de `Exception` convertía los type mismatch (`quantity: "abc"`, `quantity: 2.0`) en 500. Total de handlers: 5 (validación, cuerpo ilegible, producto no encontrado, stock insuficiente, catch-all).
- `GET /api/inventory/{code}` ahora lanza `ProductNotFoundException` en vez de devolver 404 vacío, para cumplir el criterio "GET → 404 ProblemDetail".
- El `message` de stock insuficiente es `"Stock insuficiente. Disponible: 2"`. El ejemplo del spec incluye `solicitado: 5`, pero `StockUnavailableException` no transporta la cantidad solicitada (contrato definido en B4-02). No se modificó esa firma.
- El catch-all mapea cualquier excepción MVC no manejada a 500; no hay mapeos específicos para 405 u otros errores Spring. Punto a revisar en QA.
- JDK 25 rompe Spotless 2.44.3 y los mocks de Mockito. Verificación ejecutada con `JAVA_HOME=/usr/lib/jvm/java-21-openjdk`.

## Commits

```
6c6bfd1 test(inventory): assert ProblemDetail and custom stock error
110d9a5 feat(inventory): map unexpected errors to 500 ProblemDetail
cfd52e8 feat(inventory): return custom 400 for insufficient stock
689da56 feat(inventory): return 404 ProblemDetail for missing products
91dfa66 feat(inventory): map validation failures to ProblemDetail
abd9451 feat(inventory): add API exception handler advice
```
