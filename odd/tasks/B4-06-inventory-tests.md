# ODD — B4-06: Tests de Integración (INV-01)

**Rama:** `brydyan/feature/inventory-reserve-hardening`  
**Depende de:** B4-01, B4-02, B4-03, B4-04, B4-05  
**Bloquea:** —  
**Responsable:** Andy

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

- [ ] `pom.xml` incluye testcontainers + failsafe
- [ ] `InventoryControllerTest` con 5 casos unitarios
- [ ] `InventoryReservationIT` con 5+ casos de integración
- [ ] Tests de concurrencia: 20 threads → 0 conflictos
- [ ] Tests de idempotencia: reserve y release
- [ ] CI workflow actualizado: `mvn verify`
- [ ] `mvn verify -pl inventory-service` pasa en local
- [ ] `mvn verify -pl inventory-service` pasa en CI

---

## Log

**L1:** 2026-11-23 23:45 — B4-06 creado. Tests unitarios + integración. Resuelve INV-01.
