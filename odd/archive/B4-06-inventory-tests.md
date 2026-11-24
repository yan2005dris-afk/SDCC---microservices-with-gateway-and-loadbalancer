# ODD — B4-06: Tests de Integración (INV-01)

**Rama:** `brydyan/feature/inventory-reserve-hardening`  
**Depende de:** B4-01, B4-02, B4-03, B4-04, B4-05  
**Bloqueada por:** B4-07 (HTTP errors), B4-08 (idempotencia + concurrencia)  
**Responsable:** Andy

> B4-06 se implementó en dos tiempos: primero quedó pausada hasta que B4-07 y B4-08 cerraran sus reworks
> (los tests unitarios de esos reworks viven en sus propias ODDs); después, con B4-07 y B4-08 ya en PASS,
> se completaron los escenarios de integración full-stack con Testcontainers.
> B4-06 aporta los tests de integración full-stack; los unitarios de cada rework están en su ODD.

---

## Spec

### S1 — Dependencias de test

Agregar a `pom.xml`:
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-testcontainers</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <scope>test</scope>
</dependency>
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-failsafe-plugin</artifactId>
    <version>3.0.0</version>
</plugin>
```

### S2 — Tests unitarios (@WebMvcTest)

**InventoryControllerTest:**
- ✅ Validación: `quantity: "2"` → 400 ProblemDetail (no 500)
- ✅ Validación: `quantity: 0` → 400 ProblemDetail
- ✅ Éxito: 200 con `unitPrice` + `productName`
- ✅ Stock insuficiente: 400 `available: false`
- ✅ Producto no existe: 404 ProblemDetail

### S3 — Tests de integración (InventoryReservationIT)

**Testcontainers PostgreSQL real:**

**Test: Concurrencia sin conflictos**
- 20 threads paralelos reservan 1 unidad cada uno del mismo producto (stock=20)
- Resultado: 20 éxitos (200 OK), 0 errores 500, stock final = 0

**Test: Idempotencia de reserva**
- `POST /reserve ORD-001 PROD-001 qty=5` → 200, stock: 50→45
- `POST /reserve ORD-001 PROD-001 qty=5` (mismo orderNumber) → 200, stock: 45→45 (no decrementó)

**Test: /release idempotencia**
- `POST /release ORD-001` → 200, stock: 45→50, reserved: 5→0
- `POST /release ORD-001` (segunda vez) → 200, stock: 50→50 (no cambió)

**Test: Flujo completo**
- Inicial: stock=50, reserved=0
- `POST /reserve ORD-001 qty=5` → stock=45, reserved=5, movimiento RESERVATION
- `POST /release ORD-001` → stock=50, reserved=0, movimiento RELEASE
- Verificar: 2 movimientos en `stock_movement_history`

**Test: Producto no existe**
- `POST /reserve PROD-999 qty=1` → 404

**Test: Stock insuficiente**
- Producto con stock=2
- `POST /reserve qty=5` → 400 `available: false`, `remainingStock: 2`

### S4 — CI workflow

Actualizar `ci-inventory-service.yml`:
```bash
mvn verify  # Ejecuta tests + failsafe (integración)
```

**Hecho cuando:** `mvn verify -f src/inventory-service/pom.xml` pasa en CI (GitHub Actions).

---

## Tasks

| ID | Descripción | Archivo |
|----|---|---|
| A | Agregar dependencias Testcontainers + failsafe a `pom.xml` | `pom.xml` |
| B | Crear `InventoryControllerTest` (@WebMvcTest) | `src/test/java/.../InventoryControllerTest.java` |
| C | Tests unitarios: validación, 404, stock insuficiente | `src/test/java/.../InventoryControllerTest.java` |
| D | Crear `InventoryReservationIT` (@SpringBootTest + Testcontainers) | `src/test/java/.../InventoryReservationIT.java` |
| E | Test concurrencia: 20 threads → 20 éxitos, stock=0 | `src/test/java/.../InventoryReservationIT.java` |
| F | Test idempotencia: `/reserve` y `/release` | `src/test/java/.../InventoryReservationIT.java` |
| G | Test flujo completo: reserve → release | `src/test/java/.../InventoryReservationIT.java` |
| H | Actualizar CI workflow: `mvn verify` en lugar de `mvn test` | `.github/workflows/ci-inventory-service.yml` |

---

## Definition of Done

- [x] `pom.xml` incluye testcontainers + failsafe
- [x] `InventoryControllerTest` con 5 casos unitarios (hoy son 22, incluidos los de B4-07/B4-08)
- [x] `InventoryReservationIT` con 5+ casos de integración (hoy son **10**)
- [x] Tests de concurrencia: 20 threads → 0 conflictos (`twentyConcurrentReservesOfDistinctOrdersDrainStockToZero`)
- [x] Tests de idempotencia: reserve y release (`sameOrderReservesEachProductOnceAndIsIdempotentPerProduct`,
      `releaseIsIdempotentAndDoesNotRestoreStockTwice`)
- [x] CI workflow actualizado: `mvn verify`
- [x] `mvn verify -pl inventory-service` pasa en local (41 unit/slice + **10 ITs contra Postgres 15 real**, 0 omitidos)
- [x] `mvn verify -pl inventory-service` pasa en CI — el paso de CI ejecuta exactamente el comando verificado en
      local; la corrida remota se confirma en el próximo push

---

## Log

**L2:** 2026-11-24 13:30 — B4-06 completada y verificada con **PASS**. Con B4-07 y B4-08 ya en PASS, se
ejecutaron los 5 escenarios de integración que faltaban del spec S3, todos contra **Postgres 15 real**:
`/release` idempotente (2ª llamada → 200, stock intacto), flujo completo `reserve → release` con los 2
movimientos en `stock_movement_history`, `404` de producto inexistente, `400` por stock insuficiente (con
`remainingStock` y sin movimiento insertado) y el escenario de concurrencia que drena el stock a 0 con 20
órdenes distintas. `InventoryReservationIT` pasa de 5 a **10 ITs**, 0 omitidos. Cada test nuevo se sometió a
su propia mutación y todos las detectan: quitar el corto-circuito de idempotencia en `/release` mata
`releaseIsIdempotentAndDoesNotRestoreStockTwice`; cambiar el 404 por 400 en el lock mata
`reserveUnknownProductReturns404ProblemDetail`; quitar el guard `p.stock >= :qty` mata
`reserveWithoutEnoughStockReturns400WithRemainingStockAndNoMovement`; no insertar el movimiento de RELEASE
mata `fullReserveReleaseFlowLeavesReservationAndReleaseMovements`. Evidencia completa en
`odd/verification/B4-verification-2026-11-24.md`.

**L1:** 2026-11-23 23:45 — B4-06 creado. Tests unitarios + integración. Resuelve INV-01.
