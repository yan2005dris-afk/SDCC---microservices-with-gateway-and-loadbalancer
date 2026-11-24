# ODD — B4-12: Tests de integración con PostgreSQL real en CI, sin omitir en silencio

**Rama:** `brydyan/feature/inventory-reserve-hardening`
**Depende de:** B4-08 (ITs ya escritos, nunca ejecutados), B4-10
**Coordinar con:** B4-11 (su IT de conflicto necesita a esta ODD), B4-06 (tests de integración full-stack)
**Bloquea:** B4-06
**Responsable:** Andy

> Cierra el WARN **W3** de la verificación de B4-08: los 4 tests de integración con PostgreSQL real **nunca se han
> ejecutado** y el pipeline no se entera.

---

## Por qué es ODD y no SDD

Es un **ajuste sobre una feature de pruebas existente**: cambiar un paso del pipeline, cambiar la política de
omisión de los ITs y añadir el chequeo de disponibilidad de Postgres. No hay entidad, ni dominio, ni endpoint, ni
contrato de API. Según las red lines de `01-qa-review`: ajuste a feature existente → ODD.

---

## Decisiones tomadas por el humano (2026-11-24)

> **W3 →** los ITs corren en **GitHub Actions con Testcontainers/Docker**: el workflow `ci-inventory-service.yml`
> pasa de `mvn test` a `mvn verify`.
>
> **W3b →** si no hay Postgres disponible, el build **falla con mensaje explícito**. Nada de `skipped` silencioso.

---

## El problema, con evidencia

### 1. Los 4 ITs se omiten en silencio

`src/inventory-service/src/test/java/com/distribuidos/inventory/it/InventoryReservationIT.java:28`

```java
@Testcontainers(disabledWithoutDocker = true)
```

Con el daemon de Docker caído (esta máquina: `/var/run/docker.sock` no existe), `mvn verify -pl inventory-service`
termina así:

```
[WARNING] Tests run: 4, Failures: 0, Errors: 0, Skipped: 4
[INFO] BUILD SUCCESS
```

Cuatro tests que validan **PostgreSQL real** no corrieron, y el build está verde. Nadie lo ve.

### 2. El CI ni siquiera ejecuta los ITs

`.github/workflows/ci-inventory-service.yml`:

```yaml
- name: Compile and Run Tests
  run: mvn -f src/pom.xml test -pl inventory-service     # ← `test`, no `verify`
```

`mvn test` ejecuta **surefire** (unit) pero **no** **failsafe** (integración). Los ITs no se ejecutan en CI aunque
GitHub Actions tenga Docker disponible (`ubuntu-latest` lo trae).

> Esto contradice la ODD B4-06, cuya task H pedía exactamente `mvn verify`. La task quedó sin hacer.

### 3. Qué queda sin validar mientras tanto

| IT | Qué valida | Por qué H2 no sirve |
|---|---|---|
| `sameOrderReservesEachProductOnceAndIsIdempotentPerProduct` | idempotencia y multi-línea en Postgres | — |
| `twentyConcurrentReservesForSameOrderProductReturnOkAndDecrementOnce` | el **`FOR UPDATE`** real que genera `@Lock(PESSIMISTIC_WRITE)` | H2 **no** soporta `FOR NO KEY UPDATE` (obligó a parchear la query en los probes) |
| `releaseWithoutEnoughReservedStockReturnsConflictAndKeepsInvariant` | el **`CHECK (reserved_stock >= 0)`** real | H2 no valida igual la violación de constraint |
| `schemaHasReservedStockCheckAndProductAwareUniqueIndex` | el índice **parcial** real (`WHERE reference_order_number IS NOT NULL`) y el `CHECK` en `information_schema` | H2 no soporta índices parciales |

Mientras esto no corra, el `W2` de B4-11 (el guard del invariante sin test) **no tiene arreglo posible**: su único
test es un IT de Postgres.

---

## Spec

### S1 — El pipeline ejecuta los tests de integración

**Hecho cuando:**
- `.github/workflows/ci-inventory-service.yml` corre `mvn verify` (no `mvn test`) para `inventory-service`
- el paso de CI **falla** si falla un IT (failsafe ya devuelve exit code ≠ 0)
- el workflow sigue ejecutando `spotless:check` y `checkstyle:check` antes del build

### S2 — Si no hay Postgres, el build falla con un mensaje que dice qué hacer

**Hecho cuando:**
- `InventoryReservationIT` **no** se omite en silencio: si el daemon de Docker / Postgres no está disponible, la
  ejecución **falla** con un mensaje explícito del tipo
  `"Los tests de integración de inventory-service requieren un PostgreSQL real (Docker o un Postgres accesible)."`
  y con la instrucción de qué hacer
- no se usa `disabledWithoutDocker = true` para esconder la ausencia de Postgres
- el mensaje es accionable (dice el requisito y el comando alternativo para el ciclo rápido)

### S3 — El IT de conflicto de B4-11 tiene su sitio

**Hecho cuando:**
- `InventoryReservationIT` incluye el test del camino de `ON CONFLICT DO NOTHING` (spec S4 de B4-11): la inserción
  devuelve 0 ante duplicado **sin** lanzar excepción **sin** marcar la transacción, y un `POST /reserve` repetido
  responde **200** sin cambiar el stock
- ese test **no** puede vivir en H2 (es SQL nativo de Postgres) → vive en el IT

### S4 — La ruta local sin Docker está documentada

**Hecho cuando:**
- el propio IT (o un `README` de tests) explica: con Docker → `mvn verify` corre unit + integración; sin Docker →
  `mvn test` corre solo los unit y **los ITs no se verifican** (y eso hay que decirlo, no esconderlo)

---

## Tasks

| ID | Descripción | Archivo |
|----|---|---|
| A | Cambiar el paso de tests del workflow a `mvn verify` (failsafe incluido) | `.github/workflows/ci-inventory-service.yml` |
| B | Quitar `disabledWithoutDocker = true` y añadir un chequeo explícito de disponibilidad de Postgres/Docker que **falle** con mensaje accionable | `src/test/java/.../it/InventoryReservationIT.java` |
| C | Test de conflicto para `ON CONFLICT DO NOTHING` (coordinado con B4-11, task H) | mismo archivo |
| D | Documentar la ruta local con y sin Docker | `src/test/java/.../it/InventoryReservationIT.java` (comentario de clase) o `docs/` |
| E | Verificación: `mvn -pl inventory-service verify` (con y sin Docker) + el workflow en verde | — |

---

## Definition of Done

- [ ] `.github/workflows/ci-inventory-service.yml` corre `mvn verify` para `inventory-service`
- [ ] El CI ejecuta y **exige** los 4 ITs de `InventoryReservationIT`
- [ ] Sin Postgres disponible, la ejecución **falla** con mensaje explícito (no hay `skipped` silencioso)
- [ ] El mensaje dice qué se necesita y cuál es el comando alternativo (`mvn test`) para el ciclo rápido
- [ ] El IT de conflicto `ON CONFLICT` (B4-11) está escrito y corre en CI
- [ ] La ruta local con/sin Docker está documentada
- [ ] `mvn -pl inventory-service verify` pasa en un entorno con Docker
- [ ] `mvn -pl inventory-service test` sigue pasando sin Docker (ciclo rápido)
- [ ] `mvn -pl inventory-service spotless:check` pasa

---

## Notas de implementación

**Cómo hacer que el IT falle (en vez de omitirse) con un mensaje útil:**

```java
@Testcontainers   // sin disabledWithoutDocker
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class InventoryReservationIT {

  @BeforeAll
  static void requirePostgres() {
    if (!DockerClientFactory.instance().isDockerAvailable()) {
      throw new IllegalStateException(
          "Los tests de integración de inventory-service requieren un PostgreSQL real "
              + "(Docker en ejecución o un Postgres accesible). "
              + "Para el ciclo rápido sin Docker usa: mvn test -pl inventory-service");
    }
  }
  ...
}
```

- `DockerClientFactory.instance().isDockerAvailable()` es la comprobación oficial de Testcontainers; tarda unos
  segundos la primera vez, pero da un mensaje claro.
- `disabledWithoutDocker = true` **no** se puede usar: su propósito es precisamente omitir en silencio.
- Con `@Testcontainers` sin `disabledWithoutDocker`, si falta el daemon Testcontainers lanza `IllegalStateException`
  → el IT falla → el build falla ✅. El chequeo del `@BeforeAll` solo mejora el mensaje.

**Impacto en el día a día (importante):** con esta política, `mvn verify -pl inventory-service` **falla** en una
máquina sin Docker. Es lo que se decidió, y es correcto: un `verify` verde que no corrió los ITs es peor que un
build rojo que dice la verdad. El ciclo rápido de desarrollo sigue siendo `mvn test`.

**Sobre el workflow de CI:** `ubuntu-latest` de GitHub Actions **tiene Docker** → Testcontainers levanta
`postgres:15-alpine` sin configuración extra. Si en el futuro el runner deja de tenerlo, el build fallará con el
mensaje de arriba (que es justo lo que se quiere).

---

## Comandos de verificación

```bash
cd src
JAVA_HOME=/usr/lib/jvm/java-21-openjdk mvn -pl inventory-service test      # ciclo rápido, sin Docker
JAVA_HOME=/usr/lib/jvm/java-21-openjdk mvn -pl inventory-service verify    # unit + ITs (requiere Docker)
JAVA_HOME=/usr/lib/jvm/java-21-openjdk mvn -pl inventory-service spotless:check
```

---

## Log

**L1:** 2026-11-24 11:55 — B4-12 creado. Cierra el WARN W3 de la verificación de B4-08: los 4 ITs de PostgreSQL
real nunca se ejecutaron (Docker caído → `skipped` silencioso) y el CI corre `mvn test`, que ni siquiera lanza
failsafe. Decisiones del humano: ITs en GitHub Actions con Testcontainers (`mvn verify`) y **fallar** el build si no
hay Postgres, con mensaje explícito.
