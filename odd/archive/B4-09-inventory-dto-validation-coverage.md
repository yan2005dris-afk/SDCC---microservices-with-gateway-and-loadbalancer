# ODD — B4-09: Cobertura de validación del DTO de reserva (cierre de gaps de B4-01)

**Rama:** `brydyan/feature/inventory-reserve-hardening`
**Depende de:** B4-01 (`odd/archive/B4-01-inventory-dto-validation.md`, verificado **PASS con WARN**)
**Bloquea:** B4-06
**Responsable:** Andy

> Rework de B4-01 (verificación = PASS con WARN). B4-01 queda **archivada** y no se modifica su spec salvo la
> corrección de trazabilidad ya aplicada. Esta ODD cierra los dos huecos que quedaron en la verificación.

---

## Por qué es ODD y no SDD

Es un **ajuste sobre una feature existente** (tests + documentación de trazabilidad). No hay entidad nueva,
ni dominio nuevo, ni endpoint nuevo, ni cambio de contrato de API: el comportamiento que se documenta ya existe
en producción y solo falta **fijarlo con pruebas** y **corregir los números** con que se registró. Según las
red lines de `01-qa-review`: bug fix / ajuste a feature existente → ODD.

---

## Estado real verificado (2026-11-24)

Verificación de B4-01 ejecutada con pruebas reales (MockMvc + H2) y lectura del árbol de Git:

| Qué | Estado real |
|---|---|
| `ReserveStockRequest` con `@NotBlank` / `@NotNull` / `@Positive` / `@Nullable` | ✅ correcto |
| `quantity: "abc"` / `2.0` / `-1` / `0` → 400 ProblemDetail | ✅ con test |
| `productCode: ""` → 400 ProblemDetail | ✅ con test |
| `quantity` **ausente** → 400 ProblemDetail `"quantity es requerido"` | ✅ funciona, ❌ **sin test** |
| `productCode` **ausente** → 400 ProblemDetail `"productCode no puede estar vacío"` | ✅ funciona (misma anotación), ❌ **sin test** |
| `orderNumber` ausente → válido (`@Nullable`) | ✅ funciona, ❌ **sin test** |
| Números de test en `B4-01-apply-progress.md` | ❌ **mal** (`21 → 21`); el real es `14 → 21` |
| DoD de la spec archivada de B4-01 | ❌ **sin marcar** (`- [ ]`) |

**Prueba de que `@NotNull` funciona hoy** (probe MockMvc + H2, 2026-11-24, copia temporal fuera del repo):

```
POST /api/inventory/reserve
{"productCode":"PROD-001"}

→ 400
Content-Type: application/problem+json
detail: "quantity es requerido"
```

Es decir: **no hay bug de producción aquí**. El WARN de B4-01 es de **cobertura y trazabilidad**, no de
comportamiento roto. Eso es lo que esta ODD cierra.

---

## Spec

### S1 — `quantity` ausente debe ser 400, no 500

`quantity` es `Integer` (objeto). Si el cliente lo omite, Jackson lo deja en `null`; sin `@NotNull` ese `null`
llegaría al service y se desempaquetaría en un `int` → `NullPointerException` → el catch-all
`@ExceptionHandler(Exception.class)` → **500 "Error interno del servidor"**.

Con `@NotNull` el bean validation corta **antes** del service y devuelve **400 ProblemDetail** con
`detail = "quantity es requerido"`.

**Hecho cuando:**
- `POST /api/inventory/reserve` con `{"productCode":"PROD-001"}` → **400**, `detail` = `"quantity es requerido"`
- `POST /api/inventory/reserve` con `{"productCode":"PROD-001","quantity":null}` → **400**, mismo `detail`
- el `InventoryReservationService` **no** es invocado en ninguno de los dos casos

### S2 — `productCode` ausente debe ser 400

`@NotBlank` cubre `null` y `""`. Hoy solo está fijado el caso `""`.

**Hecho cuando:**
- `POST /api/inventory/reserve` con `{"quantity":5}` → **400**, `detail` = `"productCode no puede estar vacío"`

### S3 — `orderNumber` ausente sigue siendo válido

`orderNumber` es `@Nullable` a propósito (la idempotencia es de B5). Un `null` **no** debe romper la reserva.

**Hecho cuando:**
- `POST /api/inventory/reserve` con `{"productCode":"PROD-001","quantity":5}` → **200**
- el service recibe `orderNumber == null` (no `""` ni omitido)

### S4 — Determinismo del `detail` cuando hay un solo campo inválido

`ApiExceptionHandler#handleInvalidRequest` toma el **primer** `FieldError`. Los tests nuevos deben fallar
**un solo campo** por petición, para que el `detail` esperado sea determinista.

**Hecho cuando:** ningún test nuevo envía a la vez `productCode` inválido y `quantity` inválido.

### S5 — Trazabilidad de B4-01 (ya aplicada)

- `odd/archive/B4-01-apply-progress.md`: la fila del reactor pasa de `21 → 21` a **`14 → 21`** (base `8fd860c`
  tenía 4 clases de test = 14 anotaciones `@Test`; `0a7d3c2` post-B4-01 tenía 5 clases = 21), más una nota que
  explica la corrección.
- `odd/archive/B4-01-inventory-dto-validation.md`: DoD marcado `[x]` con nota que apunta a B4-09.

**Hecho cuando:** ambos archivos están corregidos y el resto del contenido no cambia.

---

## Tasks

| ID | Descripción | Archivo |
|----|---|---|
| A | Test: `quantity` ausente → 400 `"quantity es requerido"` | `src/test/java/.../controller/InventoryControllerTest.java` |
| B | Test: `quantity: null` explícito → 400 `"quantity es requerido"` | `src/test/java/.../controller/InventoryControllerTest.java` |
| C | Test: `productCode` ausente → 400 `"productCode no puede estar vacío"` | `src/test/java/.../controller/InventoryControllerTest.java` |
| D | Test: request sin `orderNumber` → 200 y service invocado con `null` | `src/test/java/.../controller/InventoryControllerTest.java` |
| E | `verifyNoInteractions(reservationService)` en los casos de validación rechazada | `src/test/java/.../controller/InventoryControllerTest.java` |
| F | Verificar trazabilidad de B4-01 ya corregida (tarea S5, ya aplicada) | `odd/archive/B4-01-*.md` |
| G | Verificación: `mvn -pl inventory-service verify` + `spotless:check` | — |

> **No tocar** `web/ApiExceptionHandler.java`: ese archivo es territorio de **B4-07** (rework de B4-03). Esta ODD
> es solo de tests + trazabilidad, para no chocar con el otro rework.

---

## Definition of Done

- [x] Test `quantity` ausente → 400 ProblemDetail `"quantity es requerido"`
- [x] Test `quantity: null` → 400 ProblemDetail `"quantity es requerido"`
- [x] Test `productCode` ausente → 400 ProblemDetail `"productCode no puede estar vacío"`
- [x] Test sin `orderNumber` → 200 y `reserveStock("PROD-001", 5, null)`
- [x] Los 13 tests actuales de `InventoryControllerTest` siguen verdes **sin modificarlos**
- [x] `verifyNoInteractions(reservationService)` en los 3 casos de validación rechazada
- [x] Trazabilidad de B4-01 correcta (`14 → 21`, DoD marcado)
- [x] `mvn -pl inventory-service verify` pasa
- [x] `mvn -pl inventory-service spotless:check` pasa

> **DoD verificado 2026-11-24** por `03-verify-odd` → **PASS**. 4/4 tests nuevos con cobertura de mutación
> probada por ejecución (ver `odd/archive/B4-09-apply-progress.md`, sección "Verificación independiente").

---

## Notas de implementación

- **Reutiliza el helper existente** `expectProblemDetail(result, status, title, detail, instance)` de
  `InventoryControllerTest` (ya valida `type`, `title`, `status`, `detail` e `instance`). No dupliques asserts.
- **No** agregues asserts de `Content-Type: application/problem+json`: eso es de **B4-07** (task D). Si B4-07 ya
  entró a la rama, reusa su helper; si no, no lo adelantes.
- `expectProblemDetail` exige `title` = `"Bad Request"` y `instance` = `"/api/inventory/reserve"`.
- Los 3 tests de validación deben usar **`verifyNoInteractions(reservationService)`**, no `verify(...)`: la
  garantía que se quiere fijar es que la validación corta **antes** del service.
- `orderNumber` ausente se serializa en la petición sin la clave (no con `""`): `{"productCode":"PROD-001","quantity":5}`.
- Para el caso `quantity: null`, el helper `performReserve` reenvía el body tal cual → `{"productCode":"PROD-001","quantity":null}`.
- Si un test nuevo falla porque el `detail` sale distinto, **no aflojes el assert**: mira qué mensaje produce
  `FieldError::getDefaultMessage`. Los mensajes viven en el propio DTO (`ReserveStockRequest`), no en el handler.

### Por qué `@NotNull` es lo que evita el 500 (para el log de la ODD)

```
Cliente omite "quantity"
   → Jackson: record.quantity = null
   → sin @NotNull: Bean Validation pasa (null es válido para Integer)
   → service: reserveStock(code, null, order)  → unboxing a int → NullPointerException
   → catch-all @ExceptionHandler(Exception.class) → 500 "Error interno del servidor"
   → con @NotNull: Bean Validation falla antes → MethodArgumentNotValidException → 400 ProblemDetail
```

---

## Comandos de verificación

```bash
cd src
JAVA_HOME=/usr/lib/jvm/java-21-openjdk mvn -pl inventory-service verify
JAVA_HOME=/usr/lib/jvm/java-21-openjdk mvn -pl inventory-service spotless:check
```

> JDK 25 rompe Spotless 2.44.3 y los mocks de Mockito (`NoSuchMethodError` en `Log$DeferredDiagnosticHandler`).
> Fallo preexistente del entorno, no de este cambio.
>
> Estos tests son `@WebMvcTest` puros: **no** requieren Postgres ni Testcontainers, así que corren sin Docker.

**Conteos esperados:**

| Alcance | Antes | Después |
|---|---|---|
| `inventory-service` | 22 | 26 (0 failures, 0 errors) |
| Reactor completo (JDK 21) | 92 | 96 (0 failures, 0 errors) |

---

## Log

**L1:** 2026-11-24 08:55 — B4-09 creado. Cierra los 2 huecos del WARN de B4-01: falta el test de `@NotNull`
(quantity ausente) y los números de test / DoD de la spec archivada de B4-01.
