# Apply Progress — B4-01: DTOs Tipados + Validación (INV-02)

Rama: `brydyan/feature/inventory-reserve-hardening`
Base: `8fd860c`

## Tasks

- [x] A — `spring-boot-starter-validation` en `src/inventory-service/pom.xml` (`f600719`)
- [x] B — `ReserveStockRequest` record con `@NotBlank`, `@NotNull`, `@Positive`, `@Nullable` (`405376a`)
- [x] C — `ProductNotFoundException` y `StockUnavailableException` (`448f99e`)
- [x] D — `InventoryController#reserveStock` usa DTO con `@Valid`; tests de validación y regresión (`1c7301c`, `0a7d3c2`)

## Definition of Done

- [x] `pom.xml` incluye `spring-boot-starter-validation`
- [x] DTO creado con `@NotBlank`, `@NotNull`, `@Positive`
- [x] Excepciones de dominio creadas
- [x] Controller usa DTO, no `Map<String, Object>`
- [x] Tests unitarios: `quantity: "abc"`, `quantity: 2.0`, `quantity: -1`, `productCode: ""` → 400
- [x] `mvn spotless:apply && mvn verify -pl inventory-service` pasa

## Tests

| Alcance | Antes | Después |
|---|---|---|
| `inventory-service` | 0 | 7 (0 failures, 0 errors) |
| Reactor completo (JDK 21) | 14 | 21 (0 failures, 0 errors) |

> **Corrección 2026-11-24 (verificación `03-verify-odd`):** la fila del reactor decía `21 → 21`. El número real
> antes de B4-01 era **14** (4 clases de test: gateway 4+3, order 4+3; `inventory-service` sin ninguna clase de
> test). Verificado contra el árbol de la base `8fd860c`:
> `git ls-tree -r --name-only 8fd860c -- '*/src/test/*.java'` → 4 archivos, 14 anotaciones `@Test`;
> en `0a7d3c2` (post B4-01) → 5 archivos, 21 anotaciones `@Test` (inventory aporta 7).
> La fila `inventory-service 0 → 7` sí era correcta.

Evidencia RED → GREEN:
- RED: 5 tests, 2 errors (`ClassCastException: String/Double cannot be cast to Integer`) con controller basado en `Map`.
- GREEN tras DTO: `quantity: 2.0` seguía pasando por coerción de Jackson; se desactivó `ACCEPT_FLOAT_AS_INT`.
- GREEN final: 7 tests, 0 errores.

## Desviaciones necesarias

- `src/inventory-service/src/main/resources/application.yml`: `spring.jackson.deserialization.accept-float-as-int: false`. Jackson por defecto coerciona `2.0` a `2`; sin esta propiedad el caso explícito del spec devuelve 200. No estaba en la tabla de tasks, pero es requerido por el criterio de aceptación.
- Las excepciones de dominio se crean pero NO se lanzan desde el controller en B4-01. Su cableado corresponde a B4-02 (`InventoryReservationService`) y su mapeo a respuesta a B4-03 (`ApiExceptionHandler`). Esto preserva las respuestas 400 actuales de "producto no encontrado" y "stock insuficiente", cubiertas por tests de regresión.

## Blockers / TODO / gaps

- `ProblemDetail` (RFC 7807) y código 404 para producto inexistente: pendiente de B4-03.
- `orderNumber` se acepta pero no se usa: pendiente de B5 (idempotencia).
- Falta test del caso `quantity` ausente (`@NotNull`): la anotación **sí funciona** (verificado con probe MockMvc +
  H2 → `400 application/problem+json`, `detail: "quantity es requerido"`), pero no existe test en la suite que lo
  fije. Cobertura pendiente en **B4-09**.
- JDK 25 rompe Spotless 2.44.3 (`NoSuchMethodError: Log$DeferredDiagnosticHandler.getDiagnostics()`) y los mocks de Mockito en `order-service`. Verificación ejecutada con `JAVA_HOME=/usr/lib/jvm/java-21-openjdk`. Fallo preexistente del entorno, no regression de este cambio.

## Commits

```
0a7d3c2 test(inventory): pin reserve error responses while adding DTO
1c7301c feat(inventory): validate typed reserve request
448f99e feat(inventory): add domain exceptions for reserve failures
405376a feat(inventory): add typed ReserveStockRequest DTO
f600719 feat(inventory): add spring-boot-starter-validation
```

---

## Verificación (`03-verify-odd`, 2026-11-24) — PASS (WARN cerrado)

**Resultado inicial:** PASS con WARN → **WARN cerrado 2026-11-24 11:05 → veredicto final PASS**.

| Hueco del WARN | Estado | Evidencia |
|---|---|---|
| (a) falta de test para el caso `quantity` ausente (`@NotNull`) | ✅ **cerrado por B4-09** | mutación: quitar `@NotNull` de `quantity` → **2 fallos** en `InventoryControllerTest` (`@Positive` y `@NotNull` sin cobertura) → el hueco ya no existe |
| (b) números de test incorrectos en este archivo | ✅ **corregido** | la fila del reactor decía `21 → 21`; el real era **14 → 21** (nota al pie ya agregada) |

**Re-verificación con ejecución real (2026-11-24 11:05):**

- `mvn -pl inventory-service verify` → BUILD SUCCESS, 32 unit verdes ✅
- reactor `inventory-service, order-service, api-gateway` → 102 tests, 0 fallos ✅
- `mvn -q spotless:check -pl inventory-service` → exit 0 ✅
- commits convencionales, autor `Andy Alejandro <andy.alejandrovera4290@upse.edu.ec>`, sin tags de IA ✅

**Veredicto: ✅ PASS.** Se archiva en `odd/archive/`.
