# ODD — B4-11: Idempotencia real ante conflicto de índice (200 de verdad) + tests del guard de `release`

**Rama:** `brydyan/feature/inventory-reserve-hardening`
**Depende de:** B4-08 (`odd/tasks/B4-08-inventory-idempotence-concurrency.md`, verificado **WARN**), B4-10 (archivada, PASS)
**Coordinar con:** B4-12 (ITs con Postgres en CI) — **la verificación del camino de conflicto necesita el IT de Postgres**
**Bloquea:** B4-06
**Responsable:** Andy

> Rework de B4-08 (verificación = WARN). No reemplaza a B4-08: la cierra.
> B4-08 sigue en `odd/tasks/` (no se archivó) hasta que esta ODD cierre y sus criterios de aceptación pasen.

---

## Por qué es ODD y no SDD

Es un **endurecimiento sobre una feature existente**: una query de inserción que cambia, un orden de operaciones que
se corrige, dos pruebas que faltan y un invariante que hay que documentar. No hay entidad nueva, ni dominio nuevo,
ni endpoint nuevo, ni cambio del contrato de respuesta (el `remainingStock` sigue siendo el mismo campo con el mismo
significado, y `/release` sigue devolviendo los mismos campos más los dos que ya agregó B4-08). Según las red lines
de `01-qa-review`: ajuste a feature existente → ODD.

---

## Decisión tomada por el humano (2026-11-24)

> **W1 → Opción A:** el 200 idempotente debe ser **real**. Se implementa `INSERT ... ON CONFLICT DO NOTHING` para que
> la violación del índice único **no exista** y la transacción no quede *rollback-only*.

Consecuencia directa: **el orden de las operaciones cambia.** Ese es el punto técnico central de esta ODD y el
error que hay que evitar (ver S2).

---

## Qué significa el WARN de B4-08 (en claro)

B4-08 **cumplió sus criterios funcionales**: la idempotencia es por `(orderNumber, productCode)`, una orden
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
después: la transacción queda marcada *rollback-only*, de modo que el `commit* —que ocurre fuera del `try*, fuera
del método— lanza `UnexpectedRollbackException`. La API responde **500**, no 200.

Y si se fuerza la carrera de verdad (copia temporal **sin** el lock de fila), la re-lectura del movimiento ganador
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

**O sea:** hoy la garantía real la da **el lock de fila**, y el catch es código defensivo muerto que, si se activara,
devolvería 500. Con la **opción A** se añade una red real debajo del lock: si el lock falla, el conflicto se resuelve
**sin ensuciar la transacción** → 200 de verdad.

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
silencio. Lo resuelve **B4-12** (ITs en CI + skip que no se esconde). Esta ODD **necesita** a B4-12 para verificar el
camino de conflicto.

---

## Spec

### S1 — El guard `p.reservedStock >= :qty` queda fijado por una prueba que ejecuta la query

**Hecho cuando:**
- existe un test de slice JPA (H2, sin Docker) que llama a `releaseAtomically(code, qty)` con
  `reserved_stock < qty` y comprueba que **retorna 0 filas** y que la fila queda **intacta**
  (`stock`, `reserved_stock` y `version` sin cambios)
- ese test **falla** si alguien quita el guard `AND p.reservedStock >= :qty` de la query
- el test es análogo al de `reserveAtomically` que ya existe (`reserveAboveStockUpdatesNoRows`)

### S2 — El conflicto de clave se resuelve **sin** ensuciar la transacción (opción A)

**El movimiento se inserta ANTES de tocar el stock, con `ON CONFLICT DO NOTHING`.**

```
POST /reserve ORD-1 PROD-A qty=5

  1. (si hay orderNumber) lock de fila sobre PROD-A          → findByProductCodeForUpdate
  2. buscar movimiento previo (order, product, RESERVATION)   → si existe → 200 idempotente, sin escribir nada
  3. INSERT del movimiento con ON CONFLICT DO NOTHING         → 1 fila insertada | 0 filas = conflicto
       ├── 0 filas (conflicto) → NO se toca el stock → re-leer el movimiento ganador → 200 idempotente
       └── 1 fila insertada   → 4. UPDATE atómico del stock
                                  ├── 1 fila → commit: movimiento + descuento, atómicos
                                  └── 0 filas → excepción de negocio (stock insuficiente) → ROLLBACK
                                                → el movimiento insertado se deshace con la transacción
```

**Por qué el movimiento va primero (el error a evitar):** si el descuento de stock fuera **antes** del insert y el
insert_nowrightiguierase por `ON CONFLICT DO NOTHING`, la transacción **no** se marcaría *rollback-only* (no hubo
violación) → el commit **conservaría el descuento** → dos peticiones idénticas descontarían el stock dos veces
(sobreventa silenciosa). Con el movimiento primero, el conflicto se detecta **antes** de tocar el stock.

**Hecho cuando:**
- existe un método de inserción del movimiento que use
  `INSERT ... ON CONFLICT (reference_order_number, movement_type, product_code) WHERE reference_order_number IS NOT NULL DO NOTHING`
  y devuelva cuántas filas insertó
- cuando esa inserción devuelve 0 (conflicto), la API responde **200** con el movimiento ganador y **el stock no
  cambia**
- cuando la inserción devuelve 1 y el `UPDATE` de stock no tiene stock suficiente → la respuesta es el error de
  negocio de siempre (400 `available:false`) y **no queda ningún movimiento insertado**
- la migración/insert inicial de la BD usa el **mismo** índice parcial, para que el `ON CONFLICT` sea aplicable:
  `uq_stock_movement_order_product_type` sobre `(reference_order_number, movement_type, product_code)`
  `WHERE reference_order_number IS NOT NULL` (ya está en `docker/postgres/02-init-schemas.sql:67` y en
  `docker/postgres/migrate-stock-movement-index.sql`)

**Aviso técnico (importante):** `ON CONFLICT (cols) WHERE predicado DO NOTHING` **exige** que el objetivo de
conflicto coincida con el índice (columnas **y** predicado). Si el índice no fuera parcial, el `WHERE` del
`ON CONFLICT` no debe ir. El IT de Postgres debe verificar que la sentencia funciona tal cual.

**Restricción de portabilidad:** `ON CONFLICT` es **Postgres puro**. H2 no lo soporta → el camino del insert nativo
**solo se puede probar en el IT de Postgres** (ver S4). El resto de esta ODD (S1, S3) sí se prueba en H2.

### S3 — La serialización por lock de fila queda declarada y fijada

**Hecho cuando:**
- el servicio documenta explícitamente **la cadena de garantías**:
  1. **Lock de fila** (`PESSIMISTIC_WRITE` sobre la fila de producto) → es el mecanismo **principal**: serializa a
     los competidores y hace que el segundo vea el movimiento ya commiteado.
  2. **`ON CONFLICT DO NOTHING`** (opción A) → es la **red de seguridad**: si el lock no aplica (otra BD, cambio de
     aislamiento, lock movido fuera de la transacción), el conflicto se resuelve con un **200** y **sin** doble
     descuento, en vez de un 500.
  3. **Índice único** `(order, type, product)` → es lo que hace detectable el conflicto.
- un test fija que 2 reservas concurrentes del mismo `(orderNumber, productCode)` dejan **un solo movimiento** y
  **un solo descuento** (mata el mutante M10 y convierte el lock en garantía declarada)

### S4 — El camino de conflicto se prueba contra PostgreSQL real

**Hecho cuando:**
- existe un IT (Postgres, Testcontainers) que verifica que la inserción con `ON CONFLICT DO NOTHING`:
  - devuelve **1** cuando el movimiento no existe
  - devuelve **0** cuando el movimiento ya existe, **sin lanzar excepción** y **sin marcar la transacción**
  - tras el conflicto, una llamada a `POST /reserve` con el mismo `(orderNumber, productCode)` responde **200** y el
    stock **no** cambia
- el IT corre en CI (ver **B4-12**): sin Postgres el build **falla** con mensaje explícito, no se omite en silencio

### S5 — No se rompe el contrato ni la suite

**Hecho cuando:**
- la respuesta de `/reserve` y de `/release` no cambia de forma
- los 32 tests actuales de `inventory-service` siguen verdes
- `mvn -pl inventory-service verify` y `mvn -pl inventory-service spotless:check` pasan

---

## Tasks

| ID | Descripción | Archivo |
|----|---|---|
| A | Test de slice JPA: `releaseAtomically` con `reserved_stock` insuficiente → 0 filas, fila intacta (mata el mutante M1) | `src/test/java/.../repository/InventoryProductRepositoryJpaTest.java` |
| B | Test que fija la serialización por lock: 2 reservas concurrentes del mismo `(order, product)` → 1 movimiento (mata M10) | mismo archivo o `InventoryReservationServiceTest.java` |
| C | Método de inserción con `ON CONFLICT DO NOTHING` que devuelve las filas insertadas | `repository/StockMovementRepository.java` |
| D | Reordenar `reserveStock`: insertar el movimiento **antes** del UPDATE de stock y, si hubo conflicto, re-leer el ganador y responder 200 **sin tocar el stock** | `service/InventoryReservationService.java` |
| E | Reordenar `releaseStock` con la misma lógica (insertar RELEASE antes de liberar; si hubo conflicto, responder 200 sin liberar) | mismo archivo |
| F | Quitar el catch de `DataIntegrityViolationException` del camino de reserve (ya no aplica: no hay violación) y dejar el de release solo como red de seguridad, o eliminarlo | mismo archivo |
| G | Documentar en el servicio la cadena de garantías (lock → `ON CONFLICT` → índice único) | mismo archivo |
| H | IT de Postgres: la inserción con `ON CONFLICT` devuelve 0 ante duplicado sin ensuciar la transacción; `/reserve` repetido responde 200 sin cambiar el stock | `src/test/java/.../it/InventoryReservationIT.java` |
| I | Verificación: `mvn -pl inventory-service verify` + `spotless:check` + batería de mutaciones | — |

> ⚠️ **Orden de aplicación:** esta ODD toca `InventoryReservationService`, que es el archivo que el otro agente
> reescribió en B4-08. Aplicarla **después** de que termine cualquier trabajo en curso sobre ese archivo
> (`git status` limpio antes de empezar).
> La tarea H necesita **B4-12** (ITs en CI con Postgres) para poder verificarse de verdad.

---

## Definition of Done

- [ ] Test de slice JPA: `releaseAtomically` con stock reservado insuficiente → 0 filas y fila intacta
- [ ] Ese test **falla** si se quita el guard `p.reservedStock >= :qty` (mutante M1 muere)
- [ ] Test que fija la serialización por lock de fila (mutante M10 muere)
- [ ] Método de inserción con `ON CONFLICT (reference_order_number, movement_type, product_code) WHERE reference_order_number IS NOT NULL DO NOTHING`
- [ ] El movimiento se inserta **antes** del UPDATE de stock, y en caso de conflicto **no** se toca el stock
- [ ] Conflicto → **200** con el movimiento ganador, sin excepción y sin transacción *rollback-only*
- [ ] Stock insuficiente → error de negocio de siempre y **ningún** movimiento insertado (rollback)
- [ ] La cadena de garantías (lock → `ON CONFLICT` → índice único) está escrita en el código
- [ ] IT de Postgres verifica el camino de conflicto (0 filas, sin excepción, 200, stock intacto) y corre en CI
- [ ] `API_CONTRACT.md` (sección 3) sin cambios de forma: el contrato no cambia
- [ ] Los 32 tests actuales de `inventory-service` siguen verdes
- [ ] `mvn -pl inventory-service verify` pasa
- [ ] `mvn -pl inventory-service spotless:check` pasa
- [ ] Batería de mutaciones repetida: M1, M10 y M7 quedan cubiertos

---

## Notas de implementación

**Cómo insertar con `ON CONFLICT` desde Spring Data:**

```java
@Modifying
@Query(
    value =
        """
        INSERT INTO stock_movement_history (product_code, movement_type, quantity, reference_order_number)
        VALUES (:productCode, :type, :quantity, :orderNumber)
        ON CONFLICT (reference_order_number, movement_type, product_code)
          WHERE reference_order_number IS NOT NULL
        DO NOTHING
        """,
    nativeQuery = true)
int insertIfAbsent(
    @Param("productCode") String productCode,
    @Param("type") String type,
    @Param("quantity") int quantity,
    @Param("orderNumber") String orderNumber);
```

- El tipo del enum se pasa como `String` (`MovementType.RESERVATION.name()`) porque es SQL nativo.
- Con `orderNumber == null` el índice parcial no aplica → el `INSERT` entra normal. **Ojo:** si el `WHERE` del
  `ON CONFLICT` no coincide con el índice, Postgres **rechaza la sentencia**; hay que probarlo en el IT.
- Alternativa equivalente: `JdbcTemplate.update(...)` con el mismo SQL. Se prefiere la variante de repositorio para
  no mezclar dos estilos de acceso a datos en el mismo servicio.
- Este insert **no** pasa por Hibernate → la entidad `StockMovement` no queda gestionada en el contexto de
  persistencia. No importa: el movimiento ganador se **re-lee** con una query de repositorio cuando hace falta.

**Sobre el rollback del camino "stock insuficiente":** al insertar el movimiento primero, la excepción de negocio
(stock insuficiente) se lanza **después** del insert → la transacción hace rollback → el movimiento insertado
**desaparece**. Es el comportamiento correcto y es lo que hay que verificar con un test: `stock` insuficiente →
400 y **cero** movimientos de ese `orderNumber`.

**Sobre `releaseStock`:** el mismo reordenamiento aplica. Ojo con un detalle: `releaseStock` **no** tiene un `UPDATE`
de stock con guarda de "ya liberado" — hoy usa `existsByReferenceOrderNumberAndTypeAndProductCode(...)` para
detectar que ya se liberó. Con el reordenamiento, ese `existsBy` puede desaparecer: basta con insertar el RELEASE
con `ON CONFLICT DO NOTHING` y, si devuelve 0, ya estaba liberado → 200 sin tocar el stock. **Hay que decidir si se
simplifica ese camino o se deja el `existsBy`** (recomendación:Simplificar, una sola fuente de verdad para
"¿ya estaba liberado?").

**Sobre `flushAutomatically`:** `reserveAtomically` y `releaseAtomically` lo tienen. Con el movimiento insertado
primero (y por JDBC nativo, fuera de JPA), no hay cambios pendientes que volcar → el `flushAutomatically` queda
redundante pero inofensivo. No hace falta quitarlo en esta ODD.

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
> Los tests de slice (S1, S3) **no requieren Docker**. El IT de conflicto (S4) **sí** → necesita B4-12.

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

**L2:** 2026-11-24 11:55 — Decisión del humano: **opción A** (el 200 idempotente debe ser real, vía
`ON CONFLICT DO NOTHING`). S2 reescrito: el movimiento se inserta **antes** del UPDATE de stock para que un
conflicto nunca deje un descuento huérfano. Se añade S4 (IT de Postgres para el camino de conflicto) y se coordina
con **B4-12** (ITs en CI + skip que no se esconde), que es lo que permitirá verificar esa parte.
