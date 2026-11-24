# ODD — B4-10: Durabilidad de la reserva atómica (cierre de gaps de B4-02)

**Rama:** `brydyan/feature/inventory-reserve-hardening`
**Depende de:** B4-02 (`odd/archive/B4-02-inventory-atomic-reserve.md`, verificado **PASS con WARN**)
**Coordinar con:** B4-08 (idempotencia + concurrencia, rework de B4-05) — **aplicar esta ODD DESPUÉS de B4-08**
**Bloquea:** B4-06
**Responsable:** Andy

> Rework de B4-02 (verificación = PASS con WARN). B4-02 queda archivada; esta ODD cierra sus huecos.

---

## Por qué es ODD y no SDD

Es un **endurecimiento sobre una feature existente**: unaQUERY que ya funciona, una prueba que la falta y una
garantía que hay que documentar. No hay entidad nueva, ni dominio nuevo, ni endpoint nuevo, ni cambio del
contrato de respuesta (el `remainingStock` sigue siendo el mismo campo con el mismo significado). Según las
red lines de `01-qa-review`: ajuste a feature existente → ODD.

---

## Qué significa el WARN de B4-02 (en claro)

B4-02 **no falló**: el `UPDATE` condicional funciona, evita el `ObjectOptimisticLockingFailureException` y
aguanta la concurrencia (verificado contra **PostgreSQL 15 real** en la verificación: 20 hilos → 20×200 uds,
stock final 0, `reserved_stock` 20, `version` 20, 0 `ObjectOptimisticLockingFailureException`).

El WARN dice: *"el mecanismo es correcto, pero hay tres cosas que no están fijadas por ninguna prueba ni por
ningún contrato, así que el día que alguien toque el código pueden romperse en silencio"*.

### Gap 1 — La caché de Hibernate queda stale después del UPDATE masivo

**Evidencia (probe real, H2, 2026-11-24):**

```
P1 updated=1 dbStock=45 entityBefore=50 entityAfter=50 sameInstance=true
```

Traducción: la BD **sí** quedó en 45 (descontó bien), pero la entidad en la caché de primer nivel **sigue
diciendo 50**, y `findByProductCode` devuelve **la misma instancia vieja** (`sameInstance=true`).

Causa: `@Modifying` sin `clearAutomatically` no sincroniza el contexto de persistencia con el `UPDATE` masivo.

**¿Hoy rompe algo?** No: el flujo actual hace `UPDATE` primero y `SELECT` después, y en ese orden la entidad
aún no está en caché, así que el `SELECT` sí pega a la BD. **Pero es frágil**: el resultado correcto depende
del *orden* de dos llamadas, y nada lo dice en el código ni en un test. El rework **B4-08** va a reescribir este
mismo servicio → es exactamente el momento en que ese orden puede cambiar. Si cambia, la API devuelve
`remainingStock` viejo **sin ningún error visible** (el peor tipo de bug: dato silenciosamente incorrecto).

**Fix validado por ejecución** (mismo probe, con `clearAutomatically = true, flushAutomatically = true`):

```
P1 updated=1 dbStock=45 entityBefore=50 entityAfter=45 sameInstance=false
```

### Gap 2 — El `remainingStock` no es atómico por sí mismo; atómico por accidente

`remainingStock` se lee con un **segundo `SELECT`** después del `UPDATE`. Ese `SELECT` no está atómico con el
`UPDATE`: solo da la garantia correcta porque **ocurre dentro de la misma transacción**, que mantiene el lock de
fila de Postgres.

**Evidencia (probe real, H2, 2026-11-24):**

```
P2 t1 reported remainingStock=45
P2 t2updated=1 t2waitMs=444 t1reported=45 finalDbStock=44
```

Traducción: la segunda transacción **esperó 444 ms** al lock de fila hasta que la primera hizo commit. O sea,
el `UPDATE` **sí serializa** a los concurrentes, y el valor reportado por el primer cliente (45) fue el correcto.

Pero esa propiedad es **implícita**: depende de que (a) la lectura siga dentro de la transacción y (b) nadie
saque la lectura del `@Transactional`. Si alguien mueve el `SELECT` fuera del método transaccional (por ejemplo
para "optimizar" y armar la respuesta después del commit), el `remainingStock` puede incluir cambios de otros
clientes y **nada avisa**.

### Gap 3 — La suite solo usa mocks; la query nunca la parsea una BD real en CI

El test de 20 hilos (`InventoryReservationServiceTest`) **no toca ninguna base de datos**: el repositorio está
mockeado con un `AtomicInteger` y un CAS. Es un test del **algoritmo**, no de la **query**.

Consecuencias:
- El JPQL `UPDATE InventoryProduct p SET ... WHERE p.productCode = :code AND p.stock >= :qty` **nunca lo parsea
  ni lo ejecuta una BD real dentro de CI**. Si alguien lo rompe (una coma, un nombre de columna), la suite sigue
  verde.
- El caso "stock insuficiente" (`UPDATE` que afecta 0 filas) tampoco está probado contra una BD real.

En la verificación de B4-02 esto **sí se probó a mano** contra PostgreSQL 15 (y en H2), pero quedó fuera del
repo → no hay red de protección para regresiones. La prueba de integración real con Testcontainers es de
**B4-06** (bloqueada), pero un test de **slice JPA con H2** (sin Docker) sí cabe en esta ODD y cubre el hueco
crítico.

### Gap 4 — Trazabilidad

- Los números de test del `apply-progress` de B4-02 **son correctos** (`inventory-service 7 → 9`,
  `reactor 21 → 23`, verificados contra `0a7d3c2` y `eb27734`). No hay nada que corregir ahí.
- El `Definition of Done` de la spec archivada de B4-02 sigue con todas las casillas en `[ ]` → marcar con nota
  de que el test de concurrencia es mock-based (la prueba real es B4-06).

---

## Spec

### S1 — El `UPDATE` masivo no deja entidades stale en el contexto de persistencia

**Hecho cuando:**
- tras `reserveAtomically(code, qty)`, una relectura de `findByProductCode(code)` **en la misma transacción**
  devuelve el `stock` **ya descontado** (no el valor previo)
- lo mismo para `releaseAtomically(code, qty)`: la relectura devuelve el `stock` **ya restaurado**
- los 22 tests actuales de `inventory-service` siguen verdes **sin modificarlos**

### S2 — El comportamiento queda fijado por una prueba contra una base de datos real

**Hecho cuando:** existe un test de integración a nivel JPA (slice `@DataJpaTest` + H2, **sin Docker**) que:
- ejecuta `reserveAtomically` de verdad y comprueba `stock`/`reserved_stock`/`version` en la fila
- comprueba que `reserveAtomically` con `qty > stock` **no cambia nada** (0 filas afectadas)
- comprueba que la relectura ve el valor nuevo (el gap 1) → **falla si alguien quita `clearAutomatically`**

### S3 — El invariante de atomicidad del `remainingStock` queda escrito

**Hecho cuando:**
- el código deja explícito que el `SELECT` del `remainingStock` debe ejecutarse **dentro** de la transacción
  que posee el lock de fila (comentario en el método, no solo en la ODD)
- la ODD de B4-02 archivada y esta ODD documentan que el valor es "el stock restante tras mi reserva", no "el
  stock del momento de la respuesta"

### S4 — No se rompe el contrato

**Hecho cuando:** `remainingStock` sigue siendo un entero en la respuesta, con el mismo nombre y el mismo tipo.
`order-service` consume `InventoryReserveResponse` y **no se toca**.

---

## Tasks

| ID | Descripción | Archivo |
|----|---|---|
| A | `@Modifying(clearAutomatically = true, flushAutomatically = true)` en `reserveAtomically` y `releaseAtomically` | `repository/InventoryProductRepository.java` |
| B | Test de regresión de slice JPA: la relectura ve el valor nuevo (falla sin `clearAutomatically`) | `src/test/java/.../repository/InventoryProductRepositoryJpaTest.java` (nuevo) |
| C | Test de slice JPA: `qty > stock` → 0 filas, nada cambia | mismo archivo |
| D | Test de slice JPA: 20 reservas concurrentes de 1 uds → stock final 0, `reserved_stock` 20, `version` 20 | mismo archivo |
| E | Comentario en el servicio: la lectura del `remainingStock` va dentro de la tx que tiene el lock | `service/InventoryReservationService.java` |
| F | Dependencia `h2` scope `test` en `inventory-service` | `inventory-service/pom.xml` |
| G | Marcar el DoD de la spec archivada de B4-02 | `odd/archive/B4-02-inventory-atomic-reserve.md` |
| H | Verificación: `mvn -pl inventory-service verify` + `spotless:check` | — |

> ⚠️ **Coordinación con B4-08:** el otro agente está reescribiendo `InventoryReservationService` (idempotencia).
> Esta ODD **no** toca la lógica de idempotencia: solo el repositorio, el comentario y los tests nuevos.
> **Aplicar B4-10 después de B4-08** para no pisar el trabajo en curso.

---

## Definition of Done

- [x] `@Modifying` con `clearAutomatically = true` y `flushAutomatically = true` en los 2 queries
- [x] Test JPA: tras el `UPDATE` masivo, la relectura ve el `stock` nuevo (falla si se quita el flag)
- [x] Test JPA: `qty > stock` → 0 filas afectadas y la fila queda intacta
- [x] Test JPA: 20 reservas concurrentes → stock 0, `reserved_stock` 20, `version` 20, 0 excepciones
- [x] El invariante del `remainingStock` está escrito en el código
- [x] `h2` en scope `test` en `inventory-service/pom.xml`
- [x] DoD de la spec archivada de B4-02 marcado con nota
- [x] Los 22 tests actuales siguen verdes sin modificarlos
- [x] `mvn -pl inventory-service verify` pasa
- [x] `mvn -pl inventory-service spotless:check` pasa

> **DoD marcado 2026-11-24** durante la verificación `03-verify-odd` de B4-10 (resultado: **PASS**).
> El conteo real de la rama fue 29 → 32 unit (no 22 → 25), porque B4-08 ya estaba aplicada. Evidencia completa en
> `odd/archive/B4-10-apply-progress.md`.

---

## Notas de implementación

**Fix del gap 1 (probado):**

```java
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      """
      UPDATE InventoryProduct p
         SET p.stock = p.stock - :qty,
             p.reservedStock = p.reservedStock + :qty,
             p.version = p.version + 1,
             p.updatedAt = CURRENT_TIMESTAMP
       WHERE p.productCode = :code AND p.stock >= :qty
      """)
  int reserveAtomically(@Param("code") String productCode, @Param("qty") int quantity);
```

**Fix del gap 1 (probado):** `clearAutomatically = true` **descarta** el contexto de persistencia después del
UPDATE → la relectura pega a la BD. `flushAutomatically = true` **vuelca** antes del UPDATE los cambios
pendientes del contexto, para que el `UPDATE` no se ejecute sobre un estado viejo. Los dos van juntos.

**Test de slice JPA (sin Docker):**

```java
@DataJpaTest
class InventoryProductRepositoryJpaTest {

  @Autowired InventoryProductRepository repository;
  @Autowired JdbcTemplate jdbc;

  @Test
  void relecturaTrasUpdateMasivoVeElStockNuevo() {
    // seed: stock=50, reserved=0
    // reserveAtomically(code, 5)
    // assert: findByProductCode(code).getStock() == 45   <-- falla sin clearAutomatically
    // assert: jdbc "select stock, reserved_stock, version" -> 45, 5, 1
  }
}
```

- Con `@DataJpaTest`, Spring Boot **reemplaza** el datasource por el embebido (H2) → no toca el `application.yml`
  ni necesita Postgres. La dependencia `h2` debe estar en scope `test`.
- **No** pongas `@Transactional` en los tests de concurrencia: ahí lo que se quiere es que cada hilo tenga su
  transacción real (usar `TransactionTemplate` por hilo).
- H2 ejecuta el JPQL sin problema (probado). Ojo: H2 **no** es Postgres — los tipos y el DDL pueden diferir;
  para el JPQL de esta ODD da igual, y la prueba full-stack con Postgres sigue siendo de B4-06.
- El `@Version` lo incrementa el JPQL a mano (`p.version = p.version + 1`) → el test debe leer `version` de la
  fila, no del objeto.

**Sobre el invariante del `remainingStock` (gap 2):** la lectura debe quedar **dentro** del método
`@Transactional`. Si en el futuro se quiere un valor estrictamente atómico sin depender del lock, la vía es
`UPDATE ... RETURNING stock` (solo Postgres) vía `JdbcTemplate` o query nativa. **Eso es un cambio de
arquitectura del acceso a datos → no entra en esta ODD**; queda anotado para B4-06.

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
> **Los tests de esta ODD no requieren Docker**: son slice JPA con H2 embebido.

**Conteos esperados:**

| Alcance | Antes | Después |
|---|---|---|
| `inventory-service` | 22 | 25+ (0 failures, 0 errors) |
| Reactor completo (JDK 21) | 92 | 95+ (0 failures, 0 errors) |

---

## Log

**L2:** 2026-11-24 10:55 — Verificación `03-verify-odd`: **PASS**. Las 8 tasks implementadas, DoD completo, el gap
de la caché stale fijado por un test que muere al quitar `clearAutomatically`, invariante del `remainingStock`
escrito en el código y slice JPA contra base real. ODD **archivada** en `odd/archive/`.

**L1:** 2026-11-24 09:05 — B4-10 creado. Cierra los 3 huecos del WARN de B4-02 (caché stale tras `@Modifying`,
invariante de atomicidad del `remainingStock` sin documentar, suite solo-mock sin prueba de query real) + el
DoD sin marcar. Fix del gap 1 **validado por ejecución** con probe H2.
