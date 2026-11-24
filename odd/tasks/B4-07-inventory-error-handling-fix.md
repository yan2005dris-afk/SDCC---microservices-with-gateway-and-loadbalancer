# ODD — B4-07: Corrección de semántica HTTP en el manejo de errores (INV-04 rework)

**Rama:** `brydyan/feature/inventory-reserve-hardening`
**Depende de:** B4-03 (`odd/tasks/B4-03-inventory-error-handling.md`)
**Bloquea:** B4-06
**Responsable:** Andy

> Rework de B4-03 (verificación = FAIL). No reemplaza B4-03: B4-03 sigue abierta
> hasta que esta ODD cierre y sus criterios de aceptación pasen.

---

## Spec

### S1 — El catch-all no puede tragarse las excepciones HTTP de Spring

**Problema:**
`ApiExceptionHandler` declara `@ExceptionHandler(Exception.class)`. En Spring,
`ExceptionHandlerExceptionResolver` se ejecuta **antes** que `DefaultHandlerExceptionResolver`, así que
el catch-all captura también las excepciones estándar de Spring MVC y las convierte en 500.

**Evidencia (MockMvc, misma petición antes y después de B4-03):**

| Petición | `eb27734` (pre-B4-03) | HEAD | Correcto |
|---|---|---|---|
| `PUT /api/inventory/reserve` | 405 | **500** | 405 |
| `POST /api/inventory/reserve` con `Content-Type: text/plain` | 415 | **500** | 415 |
| `GET /api/inventory/a/b` (sin mapping) | 404 | **500** | 404 |

**Solución (Opción A):** `ApiExceptionHandler extends ResponseEntityExceptionHandler`.
La clase padre ya mapea las excepciones estándar de Spring MVC y devuelve `ProblemDetail` con
`Content-Type: application/problem+json`.

> ⚠️ **Trampa:** `ResponseEntityExceptionHandler.handleException(...)` ya está anotado con
> `@ExceptionHandler({... MethodArgumentNotValidException, HttpMessageNotReadableException, ErrorResponseException, ...})`.
> Declarar **otra vez** `@ExceptionHandler(MethodArgumentNotValidException.class)` en la subclase deja
> dos handlers para el mismo tipo → `IllegalStateException: Ambiguous @ExceptionHandler method mapped for [...]`.
> Los mensajes propios de validación y cuerpo ilegible se implementan **sobrescribiendo los métodos protegidos**
> del padre, no declarando `@ExceptionHandler` nuevos.

**Hecho cuando:**
- `PUT /api/inventory/reserve` → 405 ProblemDetail (no 500)
- `POST /reserve` con `Content-Type: text/plain` → 415 ProblemDetail (no 500)
- `GET /api/inventory/a/b` → 404 ProblemDetail (no 500)
- `POST /api/inventory/release` con `text/plain` → 415 ProblemDetail (no 500)

### S2 — Contrato de error intacto

Lo que B4-03 entregó bien **no se toca**:

| Excepción | HTTP | Body |
|---|---|---|
| `MethodArgumentNotValidException` | 400 | ProblemDetail, `detail` = mensaje del field (`"quantity debe ser > 0"`) |
| `HttpMessageNotReadableException` | 400 | ProblemDetail, `detail` = `"Cuerpo de solicitud inválido"` |
| `ProductNotFoundException` | 404 | ProblemDetail, `detail` = `"Producto no encontrado: <code>"` |
| `ReservationNotFoundException` | 404 | ProblemDetail (B4-05) |
| `StockUnavailableException` | 400 | custom `{productCode, available:false, remainingStock, message}` |
| `Exception` (catch-all) | 500 | ProblemDetail, `detail` = `"Error interno del servidor"` (sin stack trace al cliente) |

**Hecho cuando:** los 13 tests actuales de `InventoryControllerTest` siguen verdes **sin modificarlos**.

### S3 — El error interno queda registrado

El catch-all debe loguear la excepción completa (stack trace al log, nunca al body).

**Hecho cuando:** ante un error inesperado, el log contiene `ERROR` + excepción; la respuesta HTTP
sigue siendo 500 con `detail` genérico.

### S4 — Contrato de error fijado por tests

**Hecho cuando:** todo response de error del advice lleva `Content-Type: application/problem+json`
(assert en el helper de tests).

---

## Tasks

| ID | Descripción | Archivo |
|----|---|---|
| A | `ApiExceptionHandler extends ResponseEntityExceptionHandler` | `web/ApiExceptionHandler.java` |
| B | Sobrescribir `handleMethodArgumentNotValid`, `handleHttpMessageNotReadable` y `handleExceptionInternal` (sin duplicar `@ExceptionHandler`) | `web/ApiExceptionHandler.java` |
| C | Mantener handlers de dominio (404 / 400 custom) y catch-all con `log.error(..., exception)` | `web/ApiExceptionHandler.java` |
| D | Tests: 405, 415, 404 sin mapping, 415 en `/release` + assert de `application/problem+json` | `src/test/java/.../InventoryControllerTest.java` |
| E | Actualizar `docs/api-contracts/API_CONTRACT.md` — **solo la sección 3 (inventory-service)**: `/reserve` (body con `remainingStock`, `unitPrice`, `productName`), `/release` (body con `orderNumber` y `releases[]`), `/stock` (con `remainingStock`), y la tabla de errores (400/404/405/409/415 con `application/problem+json`) | `docs/api-contracts/API_CONTRACT.md` |
| F | Verificación: `mvn verify -pl inventory-service` + `spotless:check` | — |

---

## Definition of Done

- [ ] `ApiExceptionHandler` hereda de `ResponseEntityExceptionHandler`
- [ ] `PUT /api/inventory/reserve` → 405 ProblemDetail
- [ ] `POST /reserve` con `text/plain` → 415 ProblemDetail
- [ ] Ruta sin mapping → 404 ProblemDetail
- [ ] `POST /release` con `text/plain` → 415 ProblemDetail
- [ ] Todas las respuestas de error con `application/problem+json`
- [ ] Catch-all loguea la excepción; el body 500 sigue genérico
- [ ] `detail` de los errores de negocio sin cambios (13 tests existentes verdes)
- [ ] `docs/api-contracts/API_CONTRACT.md` — **sección 3 (inventory-service)** actualizada: `/reserve` con
      `remainingStock` + `unitPrice` + `productName`, `/release` con `orderNumber` + `releases[]`, `/stock` con
      `remainingStock`, y la tabla de errores con `application/problem+json` (400/404/405/409/415)
- [ ] La **sección 2 (order-service)** queda **sin tocar** y marcada como no revisada en esta ODD
- [ ] `mvn verify -pl inventory-service` pasa
- [ ] `mvn spotless:check -pl inventory-service` pasa

---

## Notas de implementación

```java
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status,
      WebRequest request) {
    String detail =
        ex.getBindingResult().getFieldErrors().stream()
            .findFirst()
            .map(FieldError::getDefaultMessage)
            .orElse("Solicitud inválida");
    return problemResponse(HttpStatus.BAD_REQUEST, detail, headers, status, request);
  }

  @Override
  protected ResponseEntity<Object> handleHttpMessageNotReadable(
      HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status,
      WebRequest request) {
    return problemResponse(HttpStatus.BAD_REQUEST, "Cuerpo de solicitud inválido", headers, status, request);
  }

  // handlers de dominio: NO colisionan con la lista heredada
  @ExceptionHandler(ProductNotFoundException.class)      // 404 ProblemDetail
  @ExceptionHandler(ReservationNotFoundException.class)  // 404 ProblemDetail (B4-05)
  @ExceptionHandler(StockUnavailableException.class)    // 400 custom

  @ExceptionHandler(Exception.class)                    // catch-all: log + 500 genérico
  ResponseEntity<ProblemDetail> handleUnexpected(Exception exception, HttpServletRequest request) {
    log.error("Error no controlado en {} {}", request.getMethod(), request.getRequestURI(), exception);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(problem(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno del servidor", request));
  }
}
```

- `WebRequest` puede no ser `HttpServletRequest` (en un filter llega `ServletWebRequest`) → extraer la URI con
  `instanceof` o con `request.getDescription(false)`.
- Si algún status sigue saliendo 500 tras el cambio, ese tipo no está en la lista heredada → agregar un
  `@ExceptionHandler` **concreto** para ese tipo (no genérico).
- El cuerpo custom de stock insuficiente lo consume `order-service` (`InventoryReserveResponse`) → no tocarlo.

---

## Comandos de verificación

```bash
cd src
JAVA_HOME=/usr/lib/jvm/java-21-openjdk mvn -pl inventory-service verify
JAVA_HOME=/usr/lib/jvm/java-21-openjdk mvn -pl inventory-service spotless:check
```

> JDK 25 rompe Spotless 2.44.3 y los mocks de Mockito (`NoSuchMethodError` en `Log$DeferredDiagnosticHandler`).
> Fallo preexistente del entorno, no de este cambio.

---

## Log

**L2:** 2026-11-24 11:55 — Decisión del humano sobre W4: el contrato se actualiza **solo en la sección 3
(inventory-service)**; la sección 2 (order-service) no se toca en esta ODD. Task E y DoD ampliados con la lista
explícita de lo que debe quedar reflected: bodies de `/reserve`, `/release` y `/stock`, más la tabla de errores con
`application/problem+json` y los códigos 400/404/405/409/415.

**L1:** 2026-11-24 08:44 — B4-07 creado. Rework de B4-03 tras FAIL de verificación: 405/415/404
devueltos como 500 por el catch-all, sin logging y sin tests que lo detecten.
