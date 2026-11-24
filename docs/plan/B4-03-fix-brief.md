# Brief de implementación — Fix B4-03 (Errores unificados con ProblemDetail)

> Contexto para el agente que implemente el fix. Tarea origen: `odd/tasks/B4-03-inventory-error-handling.md`.
> Rama de trabajo: `brydyan/feature/inventory-stock-mov-restock`.
> Verificación previa: **FAIL** (bug de semántica HTTP + logging + tests + contrato).

---

## 1. Qué existe y qué ya funciona (NO tocar el comportamiento)

`src/inventory-service/src/main/java/com/distribuidos/inventory/web/ApiExceptionHandler.java`
hoy tiene **6 handlers** (`@RestControllerAdvice`) y esta semántica **correcta y verificada**:

| Excepción | HTTP | Body | Test que lo fija |
|---|---|---|---|
| `MethodArgumentNotValidException` | 400 | `ProblemDetail`, `detail` = mensaje del primer field error (`"quantity debe ser > 0"`, `"productCode no puede estar vacío"`) | `rejectsNegativeQuantityWithProblemDetail`, `rejectsZeroQuantityWithProblemDetail`, `rejectsBlankProductCodeWithProblemDetail` |
| `HttpMessageNotReadableException` | 400 | `ProblemDetail`, `detail` = `"Cuerpo de solicitud inválido"` | `rejectsNonNumericQuantityWithProblemDetail`, `rejectsFractionalQuantityWithProblemDetail` |
| `ProductNotFoundException` | 404 | `ProblemDetail`, `detail` = `"Producto no encontrado: <code>"` | `getUnknownProductReturns404ProblemDetail`, `reserveUnknownProductReturns404ProblemDetail` |
| `ReservationNotFoundException` | 404 | `ProblemDetail` (agregado por B4-05) | `releaseWithoutReservationReturns404ProblemDetail` |
| `StockUnavailableException` | 400 | custom `{productCode, available:false, remainingStock, message}` | `reserveInsufficientStockReturnsCustomBadRequest` |
| `Exception` (catch-all) | 500 | `ProblemDetail`, `detail` = `"Error interno del servidor"` | `unexpectedFailureReturns500ProblemDetail` |

Los 13 tests de `InventoryControllerTest` están verdes hoy. **El fix debe dejarlos verdes, con los mismos `detail` exactos.**

---

## 2. El defecto a corregir

`@ExceptionHandler(Exception.class)` (línea 69) captura **toda** excepción, incluidas las de Spring MVC que
`DefaultHandlerExceptionResolver` debería manejar. En Spring, `ExceptionHandlerExceptionResolver` corre **antes**
que `DefaultHandlerExceptionResolver`, así que el catch-all se come los status correctos.

Evidencia empírica (MockMvc, mismas peticiones antes y después de B4-03):

| Petición | `eb27734` (pre-B4-03) | HEAD actual | Correcto |
|---|---|---|---|
| `PUT /api/inventory/reserve` | 405 | **500** | 405 |
| `POST /api/inventory/reserve` con `Content-Type: text/plain` | 415 | **500** | 415 |
| `GET /api/inventory/a/b` (sin mapping) | 404 | **500** | 404 |

Consecuencia: cualquier error de cliente (método equivocado, content-type equivocado, ruta mal escrita) se reporta
como **500**, ensucia alertas y oculta la causa real.

### Agravantes
1. **El catch-all no loguea la excepción** → se pierde causa raíz y stack trace (el `detail` al cliente debe seguir
   siendo genérico; la causa va al log).
2. **Ningún test cubre 405/415/404** → el bug pasó verde en CI.
3. **Ningún test asserta `Content-Type: application/problem+json`** → el contrato RFC 7807 no está fijado.

---

## 3. Decisión tomada: Opción A — heredar `ResponseEntityExceptionHandler`

```java
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler { ... }
```

`ResponseEntityExceptionHandler` ya trae handlers para las excepciones estándar de Spring MVC
(`HttpRequestMethodNotSupportedException` → 405, `HttpMediaTypeNotSupportedException` → 415,
`NoResourceFoundException`/`NoHandlerFoundException` → 404, `ErrorResponseException` → status propio, etc.)
y devuelve `ProblemDetail` con `Content-Type: application/problem+json`.

---

## 4. ⚠️ Trampa crítica antes de escribir código

`ResponseEntityExceptionHandler.handleException(...)` está anotado con
`@ExceptionHandler({... MethodArgumentNotValidException, HttpMessageNotReadableException, ErrorResponseException, ...})`.

Si en la subclase **también** declarás**`@ExceptionHandler(MethodArgumentNotValidException.class)`** o
**`@ExceptionHandler(HttpMessageNotReadableException.class)`**, quedan **dos handlers mapeados al mismo tipo** →
Spring lanza `IllegalStateException: Ambiguous @ExceptionHandler method mapped for [...]` al resolver la petición
(o al arrancar el contexto). **No dupliques esos `@ExceptionHandler`.**

**Patrón correcto:** los mensajes propios de validación/cuerpo ilegible se implementan **sobrescribiendo los
métodos protegidos** del padre, no declarando `@ExceptionHandler` nuevos:

```java
@Override
protected ResponseEntity<Object> handleMethodArgumentNotValid(
    MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
  String detail =
      ex.getBindingResult().getFieldErrors().stream()
          .findFirst()
          .map(FieldError::getDefaultMessage)
          .orElse("Solicitud inválida");
  return problemResponse(HttpStatus.BAD_REQUEST, detail, headers, status, request);
}

@Override
protected ResponseEntity<Object> handleHttpMessageNotReadable(
    HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
  return problemResponse(HttpStatus.BAD_REQUEST, "Cuerpo de solicitud inválido", headers, status, request);
}
```

Helper (preserva la forma `type/title/status/detail/instance` que hoy exige el contrato):

```java
private ResponseEntity<Object> problemResponse(
    HttpStatus status, String detail, HttpHeaders headers, HttpStatusCode code, WebRequest request) {
  ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
  problem.setInstance(URI.create(((HttpServletRequest) request).getRequestURI()));
  return ResponseEntity.status(code).headers(headers).body(problem);
}
```

Para que **todos** los errores estándar (405, 415, 404…) salgan con `detail`/`instance` consistentes, sobrescribí
también el formatter general:

```java
@Override
protected ResponseEntity<Object> handleExceptionInternal(
    Exception ex, Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
  ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.valueOf(status.value()), ex.getMessage());
  problem.setInstance(URI.create(((HttpServletRequest) request).getRequestURI()));
  return ResponseEntity.status(status).headers(headers).body(problem);
}
```

> `WebRequest` no siempre es `HttpServletRequest`: en un filter puede ser `ServletWebRequest`. Usá
> `request.getDescription(false)` o `HttpServletRequest` con `instanceof` para extraer la URI de forma segura.

### Handlers de dominio (NO entran en conflicto, se mantienen como están)

```java
@ExceptionHandler(ProductNotFoundException.class)      // 404 ProblemDetail
@ExceptionHandler(ReservationNotFoundException.class)  // 404 ProblemDetail (B4-05)
@ExceptionHandler(StockUnavailableException.class)    // 400 custom Map
```

### Catch-all: mantener, pero con log y sin tragarse `ErrorResponseException`

```java
private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

@ExceptionHandler(Exception.class)
ResponseEntity<ProblemDetail> handleUnexpected(Exception exception, HttpServletRequest request) {
  log.error("Error no controlado en {} {}", request.getMethod(), request.getRequestURI(), exception);
  return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
      .body(problem(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno del servidor", request));
}
```

Con la herencia, las excepciones estándar de Spring ya no llegan al catch-all (gana el handler más específico).
**Verificarlo con los tests del punto 6** — si algún status sigue saliendo 500, es que ese tipo no está cubierto
por la lista heredada: agregá un `@ExceptionHandler` explícito para ese tipo **concreto** (no genérico).

---

## 5. Requisitos transversales

- **No filtrar internals al cliente**: el 500 sigue con `detail = "Error interno del servidor"`.
- **No cambiar** el body custom de stock insuficiente (`available:false`, `remainingStock`, `message`) — lo consume
  `order-service` (`InventoryReserveResponse`).
- **No cambiar** los `detail` exactos que hoy assertan los tests (`"quantity debe ser > 0"`,
  `"productCode no puede estar vacío"`, `"Cuerpo de solicitud inválido"`, `"Producto no encontrado: X"`).
- **Logging**: el catch-all debe loguear con nivel ERROR y la excepción completa (stack trace) al log, no al body.
- **Style del repo**: `google-java-format` vía Spotless (`mvn spotless:apply`), 2 espacios, javadoc en tipos públicos.

---

## 6. Tests a agregar (`InventoryControllerTest`)

```java
@Test
void unsupportedMethodReturns405ProblemDetail() throws Exception {
  expectProblemDetail(
      mockMvc.perform(put(RESERVE_URL).contentType(MediaType.APPLICATION_JSON).content("{}")),
      405, "Method Not Allowed", null, RESERVE_URL);
}

@Test
void unsupportedMediaTypeReturns415ProblemDetail() throws Exception {
  expectProblemDetail(
      mockMvc.perform(post(RESERVE_URL).contentType(MediaType.TEXT_PLAIN).content("nope")),
      415, "Unsupported Media Type", null, RESERVE_URL);
}

@Test
void unmappedPathReturns404ProblemDetail() throws Exception {
  expectProblemDetail(
      mockMvc.perform(get("/api/inventory/a/b")),
      404, "Not Found", null, "/api/inventory/a/b");
}

@Test
void releaseWithUnsupportedMediaTypeReturns415() throws Exception { /* POST /api/inventory/release text/plain */ }
```

Además, **fijar el content type** en el helper `expectProblemDetail`:

```java
.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
```

Si el `detail` de los errores estándar no es determinista (depende del framework), ajustá el helper para no
assertar `detail` en esos 3 casos y **sí** assertar `type`, `title`, `status`, `instance` y el content type.

Los **13 tests actuales deben seguir verdes** sin modificarlos.

---

## 7. Criterios de aceptación (verificables)

| # | Criterio | Cómo se comprueba |
|---|---|---|
| A1 | `PUT /api/inventory/reserve` → **405** | test nuevo |
| A2 | `POST /reserve` con `text/plain` → **415** | test nuevo |
| A3 | ruta sin mapping → **404** | test nuevo |
| A4 | `POST /api/inventory/release` con `text/plain` → **415** | test nuevo |
| A5 | Todas las respuestas de error → `application/problem+json` | assert en helper |
| A6 | Los 13 tests actuales verdes, `detail` sin cambios | `mvn -pl inventory-service test` |
| A7 | El catch-all loguea la excepción (stack trace en log, no en body) | revisión de código + test del 500 existente |
| A8 | `spotless:check` limpio | `mvn -pl inventory-service spotless:check` |
| A9 | `mvn -pl inventory-service verify` en verde | comando local |

---

## 8. Comandos de verificación (JDK 21 obligatorio)

```bash
cd src
JAVA_HOME=/usr/lib/jvm/java-21-openjdk mvn -pl inventory-service verify
JAVA_HOME=/usr/lib/jvm/java-21-openjdk mvn -pl inventory-service spotless:check
```

> JDK 25 rompe Spotless 2.44.3 y los mocks de Mockito (`NoSuchMethodError` en `Log$DeferredDiagnosticHandler`).
> Es un fallo preexistente del entorno, no de este cambio.

---

## 9. Fuera de alcance (no tocar en este fix)

- Defectos de **B4-05** (`odd/tasks/B4-05-inventory-stock-movements.md`): idempotencia cross-product, `reservedStock`
  negativo, 500 en concurrencia. Son otro task.
- **B4-06** (tests de integración con PostgreSQL real / Testcontainers).
- `docs/api-contracts/API_CONTRACT.md`: el contrato sigue desactualizado respecto al 200/400 reales
  (INV-04 lo exigía). **Opcional** en este fix, pero recommended: documentar 404, ProblemDetail y los bodies actuales.
- `odd/` no va en `.gitignore` (quedó versionado).

---

## 10. Referencias

- ODD: `odd/tasks/B4-03-inventory-error-handling.md`
- Apply-progress: `odd/tasks/B4-03-apply-progress.md`
- Handler a corregir: `src/inventory-service/src/main/java/com/distribuidos/inventory/web/ApiExceptionHandler.java`
- Tests: `src/inventory-service/src/test/java/com/distribuidos/inventory/controller/InventoryControllerTest.java`
- Handler compartido por `/release` (B4-05): `src/inventory-service/src/main/java/com/distribuidos/inventory/exception/ReservationNotFoundException.java`
