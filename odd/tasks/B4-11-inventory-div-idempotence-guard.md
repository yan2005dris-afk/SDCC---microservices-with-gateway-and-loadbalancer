# ODD — B4-11: Garantía de idempotencia real ante violación del índice único + test del guard de `release`

**Rama:** `brydyan/feature/inventory-reserve-hardening`
**Depende de:** B4-08 (`odd/tasks/B4-08-inventory-idempotence-concurrency.md`, verificado **WARN**), B4-10
**Bloquea:** B4-06
**Responsable:** Andy

> Rework de B4-08 (verificación = WARN). No reemplaza a B4-08: la cierra.
> B4-08 sigue en `odd/tasks/` (no se archivó) hasta que esta ODD cierre y sus criterios de aceptación pasen.

---

## Por qué es ODD y no SDD

Es un **endurecimiento sobre una feature existente**: una prueba que falta, una rama de código que promete una
cosa y entrega otra, y un invariante que hay que documentar. No hay entidad nueva, ni dominio nuevo, ni endpoint
nuevo, ni cambio del contrato de respuesta. Según las red lines de `01-qa-review`: ajuste a feature existente →
ODD.

---

## Qué significa el WARN de B4-08 (en claro)

B4-08 **cumplió sus criterios funcionales**: la idempotencia ahora es por `(orderNumber, productCode)`, una orden
multi-línea reserva cada producto, 20 hilos simultáneos sobre el mismo `(orderNumber, productCode)` devuelven
20 × 200 con el stock descontado **una sola vez**, y liberar más de lo reservado devuelve **409** sin romper el
invariante de `reserved_stock`.

El WARN dice: *"el comportamiento observable es correcto, pero la garantía que dice el código no es la que el
código ofrece, y hay una parte del fix que ninguna prueba protege"*.

### Gap 1 — El catch de `DataIntegrityViolationException` promete un 200 que nunca llega

**Lo que dice la task D de B4-08 (y la errata E3):**

> Manejar `ConstraintViolationException` en INSERT: interpretarlo como idempotencia, buscar movimiento, devolver 200.

**Lo que hace el código real:**

```java
try {
  int updated = repository.reserveAtomically(productCode, quantity);
  ...
  saveMovement(productCode, MovementType.RESERVATION, quantity, orderNumber);   // save(), no saveAndFlush()
} catch (DataIntegrityViolationException exception) {
  if (!idempotent || findReservation(orderNumber, productCode).isEmpty()) {
    throw exception;
  }
}
```

**Evidencia (probe real, H2, 2026-11-24 11:05):**

```
P5a patron-sin-flush -> save() primer movimiento: ok
                     | save() duplicado lanzo DataIntegrityViolationException DENTRO del try
                     | excepcion fuera del try: UnexpectedRollbackException

P6 catch disparo; rollback-only=true
P6 commit lanzo: org.springframework.transaction.UnexpectedRollbackException :
   Transaction silently rolled back because it has been marked as rollback-only
```

Lectura: con `@GeneratedValue(strategy = IDENTITY)` el `INSERT` se ejecuta **de inmediato** en `save()`, así que la
violación del índice único salta **dentro** del `try` y el catch **sí** se dispara. El problema es lo que viene
después: la transacción queda marcada *rollback-only*, de modo que el `commit` —que ocurre fuera del `try*, fuera
del método— lanza `UnexpectedRollbackException`. La API responde **500**, no 200.

Y si la carrera se fuerza de verdad (copia temporal **sin** el lock de fila), la re-lectura del movimiento ganador
se hace sobre una sesión ya envenenada:

```
P7 ok: ReservationResponse[PROD-A, remainingStock=45, ...]
P7 EXCEPTION: org.hibernate.AssertionFailure : null id in
   com.distribuidos.inventory.model.StockMovement entry (don't flush the Session after an exception occurs)
P7 resumen ok=1 excepciones=1 fila=stock=45/reserved=5 movimientos=1
```

**Por qué hoy no se manifiesta:** `reserveStock` toma `findByProductCodeForUpdate` (`@Lock(PESSIMISTIC_WRITE)`
sobre la fila de producto) **antes** de insertar. Eso serializa a los competidores: el segundo hilo espera al
commit del primero, encuentra el movimiento ya commiteado y responde 200 por el camino idempotente normal. Probe
P2: `ok=20 errores=0 stock=49/reserved=1 movimientos=1`.

**O sea:** la garantía real la da **el lock de fila**, no el índice único ni el catch. Y eso no está escrito ni en el
código ni en la ODD. Hoy el catch es código defensivo muerto que, si alguna vez se activara, devolvería 500.

### Gap 2 — El guard del invariante de `release` no tiene ninguna prueba que lo proteja

`releaseAtomically` valida el invariante dentro de la propia query:

```sql
UPDATE InventoryProduct p
   SET p.stock = p.stock + :qty, p.reservedStock = p.reservedStock - :qty, ...
 WHERE p.productCode = :code AND p.reservedStock >= :qty
```

Este es **el fix central de la errata E2.2** (sin él, `release` de más stock revienta el `CHECK` de Postgres con
un 500). Resultado de la batería de mutaciones:

| Mutación | Resultado |
|---|---|
| quitar `AND p.reservedStock >= :qty` de `releaseAtomically` | ❌ **SOBREVIVE** — ningún test ejecutable lo detecta |
| el catch de DIV de `release` relanza siempre | ❌ SOBREVIVE |

Los tests unitarios mockean `releaseAtomically(...) -> 0` (no ejecutan la query) y el slice JPA solo libera
exactamente lo que se reservó. El único test que ejercita el guard de verdad es el IT de Postgres
`releaseWithoutEnoughReservedStockReturnsConflictAndKeepsInvariant`, que hoy queda **skipped** porque el daemon de
Docker está caído. Es decir: **si alguien quita el guard, CI sigue verde**.

### Gap 3 — El IT de concurrencia nunca se ha ejecutado

Los 4 ITs de `InventoryReservationIT` usan `@Testcontainers(disabledWithoutDocker = true)`: sin daemon se omiten en
silencio. La evidencia equivalente se obtuvo con H2 (probes P2, P4), pero el `FOR UPDATE` real de Postgres y el
`CHECK` real solo se validan en un entorno con Docker.

---

## Spec

### S1 — El guard `p.reservedStock >= :qty` queda fijado por una prueba que ejecuta la query

**Hecho cuando:**
- existe un test de slice JPA (H2, sin Docker) que llama a `releaseAtomically(code, qty)` con
  `reserved_stock < qty` y comprueba que **retorna 0 filas** y que la fila queda **intacta**
  (`stock`, `reserved_stock` y `version` sin cambios)
- ese test **falla** si alguien quita el guard `AND p.reservedStock >= :qty` de la query
- el test es análogo al de `reserveAtomically` que ya existe (`reserveAboveStockUpdatesNoRows`)

### S2 — El camino de `DataIntegrityViolationException` se define y se prueba

**Hecho cuando** (una de las dos opciones, **elegida y documentada**):

**Opción A — el 200 idempotente es real.**
- el `INSERT` del movimiento se hace de forma que la violación del índice único **no** envenene la transacción
  (`ON CONFLICT DO NOTHING` en Postgres vía query nativa, o `saveAndFlush` + re-lectura en una transacción nueva
  con `REQUIRES_NEW`)
- un test de integración (o de slice, si se puede) que provoque la violación del índice único comprueba que la
  respuesta es **200** y que el stock se descontó **una sola vez**

**Opción B — la garantía la da el lock de fila, y el código lo dice.**
- se **elimina** el catch que promete 200 o se reescribe para que no prometa lo que no puede cumplir
- el servicio documenta explícitamente que la serialización la garantiza
  `findByProductCodeForUpdate` (`PESSIMISTIC_WRITE`) sobre la fila de producto, y que el índice único es la
  **red de seguridad** para BD sin lock, no el mecanismo principal
- un test fija esa garantía: dos reservas concurrentes del mismo `(orderNumber, productCode)` → 20 × 200 con un
  solo movimiento

> **Recomendación de la verificación: opción B**, porque no cambia el modelo de acceso a datos ni introduce SQL
> nativo nuevo, y porque la evidencia (P2, P4) muestra que el lock ya serializa correctamente. La opción A solo es
> necesaria si se decide que la garantía debe sobrevivir a la pérdida del lock (por ejemplo, si el motor de BD
> cambia o si alguien mueve la lectura fuera de la transacción).
>
> Si se elige la opción A, `ON CONFLICT DO NOTHING` es SQL **nativo de Postgres**: es una query nueva en el
> repositorio, del mismo tipo que las que ya existen, pero **no** se puede probar contra H2 sin adaptarla →
> el test de esa rama tendría que vivir en el IT de Postgres.

### S3 — El invariante de la idempotencia queda escrito

**Hecho cuando:**
- el ODD de B4-08 y el servicio documentan que la idempotencia de `/reserve` descansa en **dos** mecanismos
  (lock de fila como mecanismo principal, índice único como red de seguridad) y en qué condiciones cada uno aplica
- `API_CONTRACT.md` refleja la respuesta actual de `/release` (`orderNumber` + `releases[]`), si el rework de B4-07
  todavía no lo ha hecho

### S4 — No se rompe el contrato ni la suite

**Hecho cuando:**
- la respuesta de `/reserve` y de `/release` no cambia de forma
- los 32 tests actuales de `inventory-service` siguen verdes
- `mvn -pl inventory-service verify` y `mvn -pl inventory-service spotless:check` pasan

---

## Tasks

| ID | Descripción | Archivo |
|----|---|---|
| A | Test de slice JPA: `releaseAtomically` con `reserved_stock` insuficiente → 0 filas, fila intacta (mata el mutante M1) | `src/test/java/.../repository/InventoryProductRepositoryJpaTest.java` |
| B | Test que fija la serialización por lock de fila: 2 reservas concurrentes del mismo `(order, product)` → 1 movimiento (mata M10 / fija S2-B) | mismo archivo o `InventoryReservationServiceTest.java` |
| C | Aplicar la opción elegida en S2 (A o B) en `InventoryReservationService` | `service/InventoryReservationService.java` |
| D | Documentar en el servicio la garantía real (lock de fila principal, índice único red de seguridad) | mismo archivo |
| E | Reflejar la decisión de S2 en el ODD de B4-08 (nota de verificación) y en `API_CONTRACT.md` | `odd/tasks/B4-08-...md`, `API_CONTRACT.md` |
| F | Verificación: `mvn -pl inventory-service verify` + `spotless:check` + batería de mutaciones | — |

> ⚠️ **Coordinación:** B4-08 y B4-10 ya están implementadas. Esta ODD **sí** toca
> `InventoryReservationService`, así que aplicarla **después** de que el otro agente termine cualquier trabajo en
> curso sobre ese archivo (`git status` limpio antes de empezar).

---

## Definition of Done

- [ ] Test de slice JPA: `releaseAtomically` con stock reservado insuficiente → 0 filas y fila intacta
- [ ] Ese test **falla** si se quita el guard `p.reservedStock >= :qty` (mutante M1 muere)
- [ ] Test que fija la serialización por lock de fila (mutante M10 muere)
- [ ] Decisión de S2 (A o B) aplicada en el código y documentada en el servicio
- [ ] El invariante de la idempotencia (lock + índice único) está escrito en el código y en el ODD de B4-08
- [ ] `API_CONTRACT.md` refleja la respuesta actual de `/release`
- [ ] Los 32 tests actuales de `inventory-service` siguen verdes
- [ ] `mvn -pl inventory-service verify` pasa
- [ ] `mvn -pl inventory-service spotless:check` pasa
- [ ] Batería de mutaciones repetida: los 3 huecos (M1, M10, M7) quedan cubiertos

---

## Notas de implementación

**Sobre el `UnexpectedRollbackException`:** no se arregla "capturando más excepciones". Cuando Hibernate lanza una
violación de constraint, la transacción ya está marcada *rollback-only* y la sesión puede quedar inservible
("null id ... don't flush the Session after an exception occurs"). La única forma honesta de devolver 200 es **no
producir la violación** (`ON CONFLICT DO NOTHING`) o **re-leer en una transacción nueva** (`REQUIRES_NEW`), con lo
que la transacción original se revierte (correcto: el descuento de stock de la tx perdedora también se revierte,
porque la transacción ganadora ya hizo el suyo).

**Sobre el guard de `release`:** el test de slice JPA tiene que leer la fila por JDBC (no por la entidad) para
comprobar que `stock`, `reserved_stock` y `version` no cambiaron — igual que hace el `assertRow` que ya existe en
`InventoryProductRepositoryJpaTest`.

**Sobre H2 y `PESSIMISTIC_WRITE`:** H2 no soporta `FOR NO KEY UPDATE`, que es lo que genera
`@Lock(PESSIMISTIC_WRITE)`. Cualquier test de slice con H2 que toque `findByProductCodeForUpdate` necesita o una
query nativa `for update` o no puede ejecutarse contra H2. Si se elige la opción B y el test necesita el lock real,
el sitio natural sigue siendo el IT de Postgres (Docker) y el slice H2 se limita a la query de `release`.

---

## Comandos de verificación

```bash
cd src
JAVA_HOME=/usr/lib/jvm/java-21-openjdk mvn -pl inventory-service verify
JAVA_HOME=/usr/lib/jvm/java-21-openjdk mvn -pl inventory-service spotless:check
```

> JDK 25 rompe Spotless 2.44.3 y los mocks de Mockito. Verificación con
> `JAVA_HOME=/usr/lib/jvm/java-21-openjdk`.
>
> Los tests de esta ODD **no requieren Docker** (slice JPA con H2). Los ITs de Postgres siguen requiriendo Docker.

**Conteos esperados:**

| Alcance | Antes | Después |
|---|---|---|
| `inventory-service` | 32 | 34+ (0 fallos, 0 errores) |
| Reactor completo (JDK 21) | 102 | 104+ (0 fallos, 0 errores) |

---

## Log

**L1:** 2026-11-24 11:05 — B4-11 creado. Rework del WARN de B4-08: el catch de
`DataIntegrityViolationException` promete un 200 que la transacción *rollback-only* no puede dar (evidencia P5a, P6,
P7), el guard `p.reservedStock >= :qty` no tiene prueba que lo proteja (mutante M1 sobrevive) y los ITs de
Postgres nunca se han ejecutado (Docker caído). Fix de la opción B validado por probe: el lock
`PESSIMISTIC_WRITE` sobre la fila de producto serializa a los competidores (P2: 20 hilos → 20 × 200, 1 movimiento).
